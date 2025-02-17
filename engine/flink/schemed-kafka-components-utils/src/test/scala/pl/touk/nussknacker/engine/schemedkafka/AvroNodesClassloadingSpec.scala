package pl.touk.nussknacker.engine.schemedkafka

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.definition.test.{ModelDataTestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.test.FailingContextClassloader

class AvroNodesClassloadingSpec extends AnyFunSuite with Matchers with SchemaRegistryMixin {

  override protected val schemaRegistryClient: SchemaRegistryClient = MockSchemaRegistry.getClientForScope("testScope")

  private val configCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def schemaRegistryClientFactory =
      MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

  private def withFailingLoader[T] = ThreadUtils.withThisAsContextClassLoader[T](new FailingContextClassloader) _

  private val scenario = ScenarioBuilder
    .streaming("test")
    .source(
      "source",
      "kafka",
      KafkaUniversalComponentTransformer.TopicParamName         -> "'not_exist'",
      KafkaUniversalComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
    )
    .emptySink("dead", "dead_end")

  private lazy val modelData = LocalModelData(config, configCreator)

  test("should load classes correctly for tests") {

    // we're interested only in Kafka classes loading, not in data parsing, we don't use mocks as they do not load serializers...
    withFailingLoader {
      new ModelDataTestInfoProvider(modelData).getTestingCapabilities(scenario) shouldBe TestingCapabilities.Disabled
    }
  }

}
