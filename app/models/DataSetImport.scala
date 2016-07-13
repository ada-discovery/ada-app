package models

import controllers.{ManifestedFormat, SubTypeFormat}
import reactivemongo.bson.BSONObjectID
import java.util.Date
import play.modules.reactivemongo.json.BSONFormats._
import play.api.libs.json._

abstract class DataSetImport {
  val _id: Option[BSONObjectID]
  val timeCreated: Date
  var timeLastExecuted: Option[Date]
  val dataSpaceName: String
  val dataSetId: String
  val dataSetName: String
  val createDummyDictionary: Boolean
  val scheduled: Boolean
  val scheduledTime: Option[ScheduledTime]
  val setting: Option[DataSetSetting]
}

case class ScheduledTime(hour: Option[Int], minute: Option[Int], second: Option[Int])

case class CsvDataSetImport(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: Option[String],
  delimiter: String,
  eol: Option[String],
  charsetName: Option[String],
  createDummyDictionary: Boolean,
  scheduled: Boolean,
  scheduledTime: Option[ScheduledTime],
  setting: Option[DataSetSetting],
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

case class SynapseDataSetImport(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  tableId: String,
  downloadColumnFiles: Boolean,
  downloadRecordBatchSize: Option[Int],
  createDummyDictionary: Boolean,
  scheduled: Boolean,
  scheduledTime: Option[ScheduledTime],
  setting: Option[DataSetSetting],
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

case class TranSmartDataSetImport(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  dataPath: Option[String],
  mappingPath: Option[String],
  charsetName: Option[String],
  createDummyDictionary: Boolean,
  scheduled: Boolean,
  scheduledTime: Option[ScheduledTime],
  setting: Option[DataSetSetting],
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

case class RedCapDataSetImport(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  url: String,
  token: String,
  importDictionaryFlag: Boolean,
  createDummyDictionary: Boolean,
  scheduled: Boolean,
  scheduledTime: Option[ScheduledTime],
  setting: Option[DataSetSetting],
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

object DataSetImportFormattersAndIds {
  implicit val scheduleTimeFormat = Json.format[ScheduledTime]
  implicit val dataSetSettingFormat = DataSetFormattersAndIds.dataSetSettingFormat

  implicit val dataSetImportFormat: Format[DataSetImport] = new SubTypeFormat[DataSetImport](
    Seq(
      ManifestedFormat(Json.format[CsvDataSetImport]),
      ManifestedFormat(Json.format[SynapseDataSetImport]),
      ManifestedFormat(Json.format[TranSmartDataSetImport]),
      ManifestedFormat(Json.format[RedCapDataSetImport])
    )
  )

  implicit object DataSetImportIdentity extends BSONObjectIdentity[DataSetImport] {
    def of(entity: DataSetImport): Option[BSONObjectID] = entity._id

    protected def set(entity: DataSetImport, id: Option[BSONObjectID]) =
      entity match {
        case x: CsvDataSetImport => x.copy(_id = id)
        case x: SynapseDataSetImport => x.copy(_id = id)
        case x: TranSmartDataSetImport => x.copy(_id = id)
        case x: RedCapDataSetImport => x.copy(_id = id)
      }
  }
}