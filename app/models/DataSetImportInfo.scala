package models

import controllers.{ManifestedFormat, SubTypeFormat}
import reactivemongo.bson.BSONObjectID
import java.util.Date
import play.modules.reactivemongo.json.BSONFormats._
import play.api.libs.json._

abstract class DataSetImportInfo {
  val _id: Option[BSONObjectID]
  val timeCreated: Date
  var timeLastExecuted: Option[Date]
  val dataSpaceName: String
  val dataSetId: String
  val dataSetName: String
  val scheduled: Boolean
  val scheduledTime: Option[ScheduledTime]
  val setting: Option[DataSetSetting]
}

case class ScheduledTime(hour: Option[Int], minute: Option[Int], second: Option[Int])

case class CsvDataSetImportInfo(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: Option[String],
  delimiter: String,
  eol: Option[String],
  charsetName: Option[String],
  scheduled: Boolean,
  scheduledTime: Option[ScheduledTime],
  setting: Option[DataSetSetting],
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImportInfo

case class SynapseDataSetImportInfo(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  tableId: String,
  scheduled: Boolean,
  scheduledTime: Option[ScheduledTime],
  setting: Option[DataSetSetting],
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImportInfo

case class TranSmartDataSetImportInfo(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  dataPath: Option[String],
  mappingPath: Option[String],
  charsetName: Option[String],
  scheduled: Boolean,
  scheduledTime: Option[ScheduledTime],
  setting: Option[DataSetSetting],
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImportInfo

case class RedCapDataSetImportInfo(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  url: String,
  token: String,
  importDictionaryFlag: Boolean,
  scheduled: Boolean,
  scheduledTime: Option[ScheduledTime],
  setting: Option[DataSetSetting],
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImportInfo

object DataSetImportInfoFormattersAndIds {
  implicit val scheduleTimeFormat = Json.format[ScheduledTime]
  implicit val dataSetSettingFormat = DataSetFormattersAndIds.dataSetSettingFormat

  implicit val dataSetImportInfoFormat: Format[DataSetImportInfo] = new SubTypeFormat[DataSetImportInfo](
    Seq(
      ManifestedFormat(Json.format[CsvDataSetImportInfo]),
      ManifestedFormat(Json.format[SynapseDataSetImportInfo]),
      ManifestedFormat(Json.format[TranSmartDataSetImportInfo]),
      ManifestedFormat(Json.format[RedCapDataSetImportInfo])
    )
  )

  implicit object DataSetImportInfoIdentity extends BSONObjectIdentity[DataSetImportInfo] {
    def of(entity: DataSetImportInfo): Option[BSONObjectID] = entity._id

    protected def set(entity: DataSetImportInfo, id: Option[BSONObjectID]) =
      entity match {
        case x: CsvDataSetImportInfo => x.copy(_id = id)
        case x: SynapseDataSetImportInfo => x.copy(_id = id)
        case x: TranSmartDataSetImportInfo => x.copy(_id = id)
        case x: RedCapDataSetImportInfo => x.copy(_id = id)
      }
  }
}