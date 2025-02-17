package pl.touk.nussknacker.engine.schemedkafka

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.schema._

class DefaultAvroSchemaEvolutionSpec extends AnyFunSpec with Matchers {

  val schemaEvolutionHandler: DefaultAvroSchemaEvolution = new DefaultAvroSchemaEvolution

  it("should convert record to the same schema") {
    val record = schemaEvolutionHandler.alignRecordToSchema(PaymentV1.record, PaymentV1.schema)
    record shouldBe PaymentV1.record
  }

  it("should convert record to newer compatible schema") {
    val record = schemaEvolutionHandler.alignRecordToSchema(PaymentV1.record, PaymentV2.schema)
    record shouldBe PaymentV2.record
  }

  it("should convert record to older compatible schema") {
    val record = schemaEvolutionHandler.alignRecordToSchema(PaymentV2.record, PaymentV1.schema)
    record shouldBe PaymentV1.record
  }

  it("should trow exception when try to convert record to newer not compatible schema") {
    assertThrows[AvroSchemaEvolutionException] {
      val record = schemaEvolutionHandler.alignRecordToSchema(PaymentV2.record, PaymentNotCompatible.schema)
    }
  }

  it("should convert newer not compatible record to older compatible schema") {
    val record = schemaEvolutionHandler.alignRecordToSchema(PaymentNotCompatible.record, PaymentV2.schema)
    record shouldBe PaymentV2.record
  }

  it("should trow exception when try to convert record to not compatible schema") {
    assertThrows[AvroSchemaEvolutionException] {
      val record = schemaEvolutionHandler.alignRecordToSchema(PaymentV2.record, FullNameV2.schema)
    }
  }

  it("should convert specific record to the same schema") {
    val record = schemaEvolutionHandler.alignRecordToSchema(FullNameV1.specificRecord, FullNameV1.schema)
    record shouldBe FullNameV1.specificRecord
  }

  it("should convert specific record to newer compatible schema") {
    val record = schemaEvolutionHandler.alignRecordToSchema(FullNameV1.specificRecord, FullNameV2.schema)
    record shouldBe FullNameV2.migratedGenericRecordFromV1
  }

  it("should convert specific record to older compatible schema") {
    val record = schemaEvolutionHandler.alignRecordToSchema(FullNameV2.specificRecord, FullNameV1.schema)
    record shouldBe FullNameV1.record
  }

  it("should trow exception when try to convert specific record to not compatible schema") {
    assertThrows[AvroSchemaEvolutionException] {
      schemaEvolutionHandler.alignRecordToSchema(FullNameV2.specificRecord, FullNameNotCompatible.schema)
    }
  }

  it("should convert newer not compatible specific record to older compatible schema") {
    val record = schemaEvolutionHandler.alignRecordToSchema(FullNameNotCompatible.record, FullNameV2.schema)
    record shouldBe FullNameV2.record
  }

}
