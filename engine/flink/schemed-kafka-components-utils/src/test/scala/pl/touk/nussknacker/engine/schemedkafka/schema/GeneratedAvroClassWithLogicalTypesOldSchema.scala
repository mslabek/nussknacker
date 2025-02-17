package pl.touk.nussknacker.engine.schemedkafka.schema

import java.time.{Instant, LocalDate, LocalTime}

import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.avro.specific.SpecificRecordBase

case class GeneratedAvroClassWithLogicalTypesOldSchema(
    var dateTime: Instant,
    var date: LocalDate,
    var time: LocalTime,
    var decimal: java.math.BigDecimal
) extends SpecificRecordBase {

  override def getSchema: Schema = GeneratedAvroClassWithLogicalTypesOldSchema.schema

  override def get(field: Int): AnyRef = field match {
    case 0 => dateTime
    case 1 => date
    case 2 => time
    case 3 => decimal
    case _ => throw new AvroRuntimeException("Bad index")
  }

  override def put(field: Int, value: Any): Unit = field match {
    case 0 => dateTime = value.asInstanceOf[Instant]
    case 1 => date = value.asInstanceOf[LocalDate]
    case 2 => time = value.asInstanceOf[LocalTime]
    case 3 => decimal = value.asInstanceOf[java.math.BigDecimal]
    case _ => throw new AvroRuntimeException("Bad index")
  }

}

object GeneratedAvroClassWithLogicalTypesOldSchema extends TestSchema {

  override def stringSchema: String =
    """{
      |  "type": "record",
      |  "name": "GeneratedAvroClassWithLogicalTypes",
      |  "namespace": "pl.touk.nussknacker.engine.schemedkafka.schema",
      |  "fields": [
      |    {
      |      "name": "dateTime",
      |      "type": [
      |        "null",
      |        {
      |          "type": "long",
      |          "logicalType": "timestamp-millis"
      |        }
      |      ],
      |      "default": null
      |    },
      |    {
      |      "name": "date",
      |      "type": [
      |        "null",
      |        {
      |          "type": "int",
      |          "logicalType": "date"
      |        }
      |      ],
      |      "default": null
      |    },
      |    {
      |      "name": "time",
      |      "type": [
      |        "null",
      |        {
      |          "type": "int",
      |          "logicalType": "time-millis"
      |        }
      |      ],
      |      "default": null
      |    },
      |    {
      |      "name": "decimal",
      |      "type": [
      |        "null",
      |        {
      |          "type": "bytes",
      |          "logicalType": "decimal",
      |          "precision": 4,
      |          "scale": 2
      |        }
      |      ],
      |      "default": null
      |    }
      |  ]
      |}""".stripMargin

}
