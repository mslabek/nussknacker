package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService
import pl.touk.nussknacker.engine.CustomProcessValidatorLoader
import pl.touk.nussknacker.engine.Interpreter.IOShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentInfo, ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.spel.SpelConversionsProvider
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.definition.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.text.ParseException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class SpelConversionServiceOverrideSpec extends AnyFunSuite with Matchers with OptionValues {

  private implicit class ValidatedValue[E, A](validated: ValidatedNel[E, A]) {
    def value: A = validated.valueOr(err => throw new ParseException(err.toList.mkString, -1))
  }

  class SomeService extends Service {
    @MethodToInvoke
    def invoke(@ParamName("listParam") param: java.util.List[Any]): Future[Any] = Future.successful(param)
  }

  class MyProcessConfigCreator(spelCustomConversionsProviderOpt: Option[SpelConversionsProvider])
      extends EmptyProcessConfigCreator {

    override def sourceFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SourceFactory]] =
      Map(
        "stringSource" -> WithCategories(
          SourceFactory.noParam[String](new pl.touk.nussknacker.engine.api.process.Source {})
        )
      )

    override def services(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[Service]] = {
      Map("service" -> WithCategories(new SomeService))
    }

    override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
      ExpressionConfig(
        globalProcessVariables = Map("CONV" -> WithCategories(ConversionUtils)),
        globalImports = List.empty,
        customConversionsProviders = spelCustomConversionsProviderOpt.toList
      )
    }

  }

  test("be able to override Nussknacker default spel conversion service") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "stringSource")
      // here is done conversion from comma separated string to list[string] which is currently not supported by nussknacker typing
      // system so is also disabled in spel evaluation but can be turned on by passing customConversionsProviders with SpEL's DefaultConversionService
      .enricher("invoke-service", "output", "service", "listParam" -> "#CONV.toAny(#input)")
      .processorEnd("dummy", "service", "listParam" -> "{}")
    val inputValue = "123,234"

    interpret(process, None, inputValue) should matchPattern {
      case Invalid(
            NonEmptyList(
              NuExceptionInfo(
                Some(NodeComponentInfo("invoke-service", Some(ComponentInfo("service", ComponentType.Enricher)))),
                ex,
                _
              ),
              Nil
            )
          ) if ex.getMessage.contains("cannot convert from java.lang.String to java.util.List<?>") =>
    }

    val defaultSpelConversionServiceProvider = new SpelConversionsProvider {
      override def getConversionService: ConversionService =
        new DefaultConversionService
    }
    val outputValue =
      interpret(process, Some(defaultSpelConversionServiceProvider), inputValue).value.finalContext[AnyRef]("output")
    outputValue shouldEqual List("123", "234").asJava
  }

  private def interpret(
      process: CanonicalProcess,
      spelCustomConversionsProviderOpt: Option[SpelConversionsProvider],
      inputValue: Any
  ) = {
    val modelData = LocalModelData(ConfigFactory.empty(), new MyProcessConfigCreator(spelCustomConversionsProviderOpt))
    val compilerData = ProcessCompilerData.prepare(
      process,
      modelData.modelDefinitionWithTypes,
      modelData.engineDictRegistry,
      FragmentComponentDefinitionExtractor(modelData),
      Seq.empty,
      getClass.getClassLoader,
      ProductionServiceInvocationCollector,
      ComponentUseCase.EngineRuntime,
      CustomProcessValidatorLoader.emptyCustomProcessValidator
    )
    val parts  = compilerData.compile().value
    val source = parts.sources.head
    val compiledNode =
      compilerData.subPartCompiler.compile(source.node, source.validationContext)(process.metaData).result.value

    val inputContext = Context("foo").withVariable(VariableConstants.InputVariableName, inputValue)
    Validated
      .fromEither(
        compilerData.interpreter.interpret(compiledNode, parts.metaData, inputContext).unsafeRunSync().head.swap
      )
      .toValidatedNel
  }

  object ConversionUtils extends HideToString {

    def toAny(@ParamName("value") value: Any): Any = {
      value
    }

  }

}
