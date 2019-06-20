package org.ada.server.models.datatrans

import java.util.Date

import org.ada.server.dataaccess.{BSONObjectIdentity, StreamSpec}
import org.ada.server.json._
import org.ada.server.models.{Schedulable, ScheduledTime, StorageType, WeekDay}
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

trait DataSetMetaTransformation extends Schedulable {
  val _id: Option[BSONObjectID]
  val timeCreated: Date
  val timeLastExecuted: Option[Date]

  val sourceDataSetIds: Seq[String]

  def copyCore(
    _id: Option[BSONObjectID],
    timeCreated: Date,
    timeLastExecuted: Option[Date],
    scheduled: Boolean,
    scheduledTime: Option[ScheduledTime]
  ): DataSetMetaTransformation
}

trait DataSetTransformation extends DataSetMetaTransformation {
  val streamSpec: StreamSpec
  val resultDataSetSpec: ResultDataSetSpec
  val resultDataSetId = resultDataSetSpec.id
  val resultDataSetName = resultDataSetSpec.name
  val resultStorageType = resultDataSetSpec.storageType
}

case class ResultDataSetSpec(
  id: String,
  name: String,
  storageType: StorageType.Value
)

object DataSetTransformation {

  implicit val weekDayFormat = EnumFormat(WeekDay)
  implicit val scheduleTimeFormat = Json.format[ScheduledTime]
  implicit val storageTypeFormat = EnumFormat(StorageType)
  implicit val resultDataSetSpecFormat = Json.format[ResultDataSetSpec]
  implicit val streamSpecFormat = Json.format[StreamSpec]
  implicit val tupleFormat = TupleFormat[String, String]
  implicit val tupleFormat2 = TupleFormat[String, String, String]
  implicit val optionIntFormat = new OptionFormat[Int]
  implicit val tupleFormat3 = TupleFormat[String, Option[Int]]
  implicit val linkedDataSetSpecFormat = Json.format[LinkedDataSetSpec]

  implicit val dataSetMetaTransformationFormat: Format[DataSetMetaTransformation] = new SubTypeFormat[DataSetMetaTransformation](
    Seq(
      ManifestedFormat(Json.format[CopyDataSetTransformation]),
      ManifestedFormat(Json.format[DropFieldsTransformation]),
      ManifestedFormat(Json.format[RenameFieldsTransformation]),
      ManifestedFormat(Json.format[ChangeFieldEnumsTransformation]),
      ManifestedFormat(Json.format[MatchGroupsWithConfoundersTransformation]),
      ManifestedFormat(Json.format[LinkTwoDataSetsTransformation]),
      ManifestedFormat(Json.format[LinkMultiDataSetsTransformation])
    )
  )

  implicit object DataSetMetaTransformationIdentity extends BSONObjectIdentity[DataSetMetaTransformation] {
    def of(entity: DataSetMetaTransformation): Option[BSONObjectID] = entity._id

    protected def set(entity: DataSetMetaTransformation, id: Option[BSONObjectID]) =
      entity.copyCore(id, entity.timeCreated, entity.timeLastExecuted, entity.scheduled, entity.scheduledTime)
  }
}