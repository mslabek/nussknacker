package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase.EngineRuntime
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.test.{RunResult, TestScenarioRunner}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.util
import scala.concurrent.Future

class LiteTestScenarioRunnerSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("should test custom component with lite") {
    val scenario = ScenarioBuilder
      .streamingLite("t1")
      .source("source", TestScenarioRunner.testDataSource)
      // we test component created manually
      .enricher("customByHand", "o1", "customByHand", "param" -> "#input")
      // we test component registered via normal ConfigProvider
      .enricher("custom", "o2", "custom", "param" -> "#input")
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "{#o1, #o2}")

    val runner = new LiteTestScenarioRunner(
      List(ComponentDefinition("customByHand", new CustomComponent("myPrefix"))),
      Map.empty,
      ConfigFactory.empty().withValue("components.custom.prefix", fromAnyRef("configuredPrefix")),
      EngineRuntime
    )

    val result = runner.runWithData[String, java.util.List[String]](scenario, List("t1"))
    result.validValue shouldBe RunResult.success(util.Arrays.asList("myPrefix:t1", "configuredPrefix:t1"))
  }

  test("should access source property") {
    val scenario = ScenarioBuilder
      .streamingLite("t1")
      .source("source", TestScenarioRunner.testDataSource)
      .buildVariable("v", "v", "varField" -> "#input.field")
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#v.varField")

    val runner = new LiteTestScenarioRunner(List.empty, Map.empty, ConfigFactory.empty(), EngineRuntime)

    val result = runner.runWithData[SourceData, String](scenario, List(SourceData("abc")))
    result.validValue shouldBe RunResult.success("abc")
  }

}

private case class SourceData(field: String)

class CustomComponentProvider extends ComponentProvider {
  override def providerName: String = "custom"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] =
    List(ComponentDefinition("custom", new CustomComponent(config.getString("prefix"))))

  override def isCompatible(version: NussknackerVersion): Boolean = true
}

class CustomComponent(prefix: String) extends Service {
  @MethodToInvoke
  def invoke(@ParamName("param") input: String): Future[String] = Future.successful(s"$prefix:$input")
}
