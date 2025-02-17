package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  EmptyMandatoryParameter,
  ExpressionParserCompilationError,
  WrongParameters
}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.compile.validationHelpers._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ModelDefinitionWithTypes
import pl.touk.nussknacker.engine.definition.{FragmentComponentDefinitionExtractor, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.definition.parameter.editor.ParameterTypeEditorDeterminer
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, spel}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

class GenericTransformationValidationSpec extends AnyFunSuite with Matchers with OptionValues with Inside {

  import spel.Implicits._

  object MyProcessConfigCreator extends EmptyProcessConfigCreator {

    override def customStreamTransformers(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
      "genericParameters" -> WithCategories(GenericParametersTransformer),
      "genericJoin"       -> WithCategories(DynamicParameterJoinTransformer),
      "twoStepsInOne"     -> WithCategories(GenericParametersTransformerWithTwoStepsThatCanBeDoneInOneStep),
      "paramsLoop"        -> WithCategories(ParamsLoopNode)
    )

    override def sourceFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SourceFactory]] = Map(
      "mySource"                -> WithCategories(SimpleStringSource),
      "genericParametersSource" -> WithCategories(new GenericParametersSource)
    )

    override def sinkFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SinkFactory]] = Map(
      "dummySink"              -> WithCategories(SinkFactory.noParam(new Sink {})),
      "genericParametersSink"  -> WithCategories(GenericParametersSink),
      "optionalParametersSink" -> WithCategories(OptionalParametersSink),
    )

    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
      Map(
        "genericParametersProcessor"         -> WithCategories(GenericParametersProcessor),
        "genericParametersEnricher"          -> WithCategories(GenericParametersEnricher),
        "genericParametersThrowingException" -> WithCategories(GenericParametersThrowingException)
      )

  }

  private val processBase = ScenarioBuilder.streaming("proc1").source("sourceId", "mySource")

  private val objectWithMethodDef = ProcessDefinitionExtractor.extractObjectWithMethods(
    MyProcessConfigCreator,
    getClass.getClassLoader,
    process.ProcessObjectDependencies(ConfigFactory.empty, ObjectNamingProvider(getClass.getClassLoader))
  )

  private val fragmentDefinitionExtractor =
    FragmentComponentDefinitionExtractor(ConfigFactory.empty, getClass.getClassLoader)

  private val validator = ProcessValidator.default(
    ModelDefinitionWithTypes(objectWithMethodDef),
    fragmentDefinitionExtractor,
    new SimpleDictRegistry(Map.empty),
    CustomProcessValidatorLoader.emptyCustomProcessValidator
  )

  private val expectedGenericParameters = List(
    Parameter[String]("par1")
      .copy(editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)), defaultValue = Some("''")),
    Parameter[Long]("lazyPar1").copy(isLazyParameter = true, defaultValue = Some("0")),
    Parameter("val1", Unknown),
    Parameter("val2", Unknown),
    Parameter("val3", Unknown)
  )

  test("should validate happy path") {
    val result = validator.validate(
      processBase
        .customNode(
          "generic",
          "out1",
          "genericParameters",
          "par1"     -> "'val1,val2,val3'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1"     -> "'aa'",
          "val2"     -> "11",
          "val3"     -> "{false}"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Symbol("valid")
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(
      Map(
        "val1" -> Typed.fromInstance("aa"),
        "val2" -> Typed.fromInstance(11),
        "val3" -> Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.fromInstance(false)))
      )
    )

    result.parametersInNodes("generic") shouldBe expectedGenericParameters
  }

  test("should validate sources") {
    val result = validator.validate(
      ScenarioBuilder
        .streaming("proc1")
        .source(
          "sourceId",
          "genericParametersSource",
          "par1"     -> "'val1,val2,val3'",
          "lazyPar1" -> "'ll' == null ? 1 : 5",
          "val1"     -> "'aa'",
          "val2"     -> "11",
          "val3"     -> "{false}"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Symbol("valid")
    val info1 = result.typing("end")

    info1.inputValidationContext("otherNameThanInput") shouldBe TypedObjectTypingResult(
      Map(
        "val1" -> Typed.fromInstance("aa"),
        "val2" -> Typed.fromInstance(11),
        "val3" -> Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.fromInstance(false)))
      )
    )

    result.parametersInNodes("sourceId") shouldBe expectedGenericParameters
  }

  test("should validate sinks") {
    val result = validator.validate(
      processBase.emptySink(
        "end",
        "genericParametersSink",
        "par1"     -> "'val1,val2,val3'",
        "lazyPar1" -> "#input == null ? 1 : 5",
        "val1"     -> "'aa'",
        "val2"     -> "11",
        "val3"     -> "{false}"
      )
    )
    result.result shouldBe Symbol("valid")

    result.parametersInNodes("end") shouldBe expectedGenericParameters
  }

  test("should validate services") {
    val result = validator.validate(
      processBase
        .processor(
          "genericProcessor",
          "genericParametersProcessor",
          "par1"     -> "'val1,val2,val3'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1"     -> "'aa'",
          "val2"     -> "11",
          "val3"     -> "{false}"
        )
        .enricher(
          "genericEnricher",
          "out",
          "genericParametersProcessor",
          "par1"     -> "'val1,val2,val3'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1"     -> "'aa'",
          "val2"     -> "11",
          "val3"     -> "{false}"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Symbol("valid")

    result.parametersInNodes("genericProcessor") shouldBe expectedGenericParameters
    result.parametersInNodes("genericProcessor") shouldBe expectedGenericParameters
  }

  test("should handle exception throws during validation gracefully") {
    val result = validator.validate(
      processBase
        .processor(
          "genericProcessor",
          "genericParametersThrowingException",
          "par1"     -> "'val1,val2,val3'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1"     -> "'aa'",
          "val2"     -> "11",
          "val3"     -> "{false}"
        )
        .emptySink("end", "dummySink")
    )

    result.parametersInNodes("genericProcessor") shouldBe expectedGenericParameters
  }

  test("should dependent parameter in sink") {
    val result = validator.validate(
      processBase.emptySink(
        "end",
        "genericParametersSink",
        "par1"     -> "'val1,val2'",
        "lazyPar1" -> "#input == null ? 1 : 5",
        "val1"     -> "''"
      )
    )
    result.result should matchPattern { case Invalid(NonEmptyList(EmptyMandatoryParameter(_, _, "val2", "end"), Nil)) =>
    }

    val parameters = result.parametersInNodes("end")
    parameters shouldBe List(
      Parameter[String]("par1")
        .copy(editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)), defaultValue = Some("''")),
      Parameter[Long]("lazyPar1").copy(isLazyParameter = true, defaultValue = Some("0")),
      Parameter("val1", Unknown),
      Parameter("val2", Unknown)
    )
  }

  test("should find wrong determining parameter") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters", "par1" -> "12", "lazyPar1" -> "#input == null ? 1 : 5")
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(
      NonEmptyList.of(
        ExpressionParserCompilationError(
          s"Bad expression type, expected: String, found: ${Typed.fromInstance(12).display}",
          "generic",
          Some("par1"),
          "12"
        )
      )
    )
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map.empty[String, TypingResult])

  }

  test("should find wrong dependent parameters") {

    val result = validator.validate(
      processBase
        .customNode(
          "generic",
          "out1",
          "genericParameters",
          "par1"     -> "'val1,val2'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1"     -> "''"
        )
        .emptySink("end", "dummySink")
    )
    result.result should matchPattern {
      case Invalid(NonEmptyList(EmptyMandatoryParameter(_, _, "val2", "generic"), Nil)) =>
    }

    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(
      Map(
        "val1" -> Typed.fromInstance(""),
        "val2" -> Unknown
      )
    )

    val parameters = result.parametersInNodes("generic")
    parameters shouldBe List(
      Parameter[String]("par1")
        .copy(editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)), defaultValue = Some("''")),
      Parameter[Long]("lazyPar1").copy(isLazyParameter = true, defaultValue = Some("0")),
      Parameter("val1", Unknown),
      Parameter("val2", Unknown)
    )
  }

  test("should find no output variable") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters", "par1" -> "12", "lazyPar1" -> "#input == null ? 1 : 5")
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(
      NonEmptyList.of(
        ExpressionParserCompilationError(
          s"Bad expression type, expected: String, found: ${Typed.fromInstance(12).display}",
          "generic",
          Some("par1"),
          "12"
        )
      )
    )
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map.empty[String, TypingResult])
  }

  test("should compute dynamic parameters in joins") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "mySource")
          .buildSimpleVariable("var1", "intVal", "123")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .buildSimpleVariable("var2", "strVal", "'abc'")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join(
            "join1",
            "genericJoin",
            Some("outPutVar"),
            List(
              "branch1" -> List("isLeft" -> "true"),
              "branch2" -> List("isLeft" -> "false")
            ),
            "rightValue" -> "#strVal + 'dd'"
          )
          .emptySink("end", "dummySink")
      )
    val validationResult = validator.validate(process)

    val varsInEnd = validationResult.variablesInNodes("end")
    varsInEnd("outPutVar") shouldBe Typed.fromInstance("abcdd")
    varsInEnd("intVal") shouldBe Typed.fromInstance(123)
    varsInEnd.get("strVal") shouldBe None
  }

  test("should validate optional parameter default value") {
    val process = processBase
      .emptySink("optionalParameters", "optionalParametersSink", "wrongOptionalParameter" -> "'123'")

    val result = validator.validate(process)

    val parameters = result.parametersInNodes("optionalParameters")
    parameters shouldBe List(
      Parameter
        .optional[CharSequence]("optionalParameter")
        .copy(editor = new ParameterTypeEditorDeterminer(Typed[CharSequence]).determine(), defaultValue = Some(""))
    )
  }

  test("should be possible to perform two steps of validation in one step using defaults as node parameters") {
    val result = validator.validate(
      processBase
        .customNodeNoOutput("generic", "twoStepsInOne")
        .emptySink("end", "dummySink")
    )

    result.result shouldBe Symbol("valid")
    val parameterNames = result.parametersInNodes("generic").map(_.name)
    parameterNames shouldEqual List("moreParams", "extraParam")
  }

  test("should omit redundant parameters for generic transformations") {
    val result = validator.validate(
      processBase
        .customNodeNoOutput("generic", "twoStepsInOne", "redundant" -> "''")
        .emptySink("end", "dummySink")
    )

    result.result shouldBe Symbol("valid")
    val parameterNames = result.parametersInNodes("generic").map(_.name)
    parameterNames shouldEqual List("moreParams", "extraParam")
  }

  test("should not fall in endless loop for buggy node implementation") {
    val result = validator.validate(
      processBase
        .customNodeNoOutput("generic", "paramsLoop")
        .emptySink("end", "dummySink")
    )

    result.result shouldBe Validated.invalidNel(WrongParameters(Set.empty, Set.empty)(NodeId("generic")))
  }

}
