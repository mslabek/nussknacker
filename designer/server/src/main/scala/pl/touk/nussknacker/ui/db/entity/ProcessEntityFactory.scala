package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp

trait ProcessEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity] = LTableQuery(new ProcessEntity(_))

  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {

    def id: Rep[ProcessId] = column[ProcessId]("id", O.PrimaryKey, O.AutoInc)

    def name: Rep[ProcessName] = column[ProcessName]("name", NotNull)

    def description: Rep[Option[String]] = column[Option[String]]("description", O.Length(1000))

    def processCategory: Rep[String] = column[String]("category", NotNull)

    def processingType: Rep[ProcessingType] = column[ProcessingType]("processing_type", NotNull)

    def isFragment: Rep[Boolean] = column[Boolean]("is_fragment", NotNull)

    def isArchived: Rep[Boolean] = column[Boolean]("is_archived", NotNull)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def createdBy: Rep[String] = column[String]("created_by", NotNull)

    def * : ProvenShape[ProcessEntityData] =
      (id, name, description, processCategory, processingType, isFragment, isArchived, createdAt, createdBy) <> (
        ProcessEntityData.apply _ tupled, ProcessEntityData.unapply
      )

  }

}

final case class ProcessEntityData(
    id: ProcessId,
    name: ProcessName,
    description: Option[String],
    processCategory: String,
    processingType: ProcessingType,
    isFragment: Boolean,
    isArchived: Boolean,
    createdAt: Timestamp,
    createdBy: String
)
