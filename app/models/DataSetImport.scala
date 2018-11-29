package models

import models.{DataSetFormattersAndIds, DataSetSetting}
import dataaccess.BSONObjectIdentity
import models.json.{ManifestedFormat, SubTypeFormat}
import reactivemongo.bson.BSONObjectID
import java.util.Date
import reactivemongo.play.json.BSONFormats._
import play.api.libs.json._

abstract class DataSetImport {
  val _id: Option[BSONObjectID]
  val timeCreated: Date
  var timeLastExecuted: Option[Date]
  val dataSpaceName: String
  val dataSetId: String
  val dataSetName: String
  val scheduled: Boolean
  val scheduledTime: Option[ScheduledTime]
  val setting: Option[DataSetSetting]
  val dataView: Option[DataView]
}

case class ScheduledTime(hour: Option[Int], minute: Option[Int], second: Option[Int])

case class CsvDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: Option[String] = None,
  delimiter: String,
  eol: Option[String] = None,
  charsetName: Option[String] = None,
  matchQuotes: Boolean,
  inferFieldTypes: Boolean,
  inferenceMaxEnumValuesCount: Option[Int] = None,
  inferenceMinAvgValuesPerEnum: Option[Double] = None,
  arrayDelimiter: Option[String] = None,
  booleanIncludeNumbers: Boolean = false,
  saveBatchSize: Option[Int] = None,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  setting: Option[DataSetSetting] = None,
  dataView: Option[DataView] = None,
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

case class JsonDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: Option[String] = None,
  charsetName: Option[String] = None,
  inferFieldTypes: Boolean,
  inferenceMaxEnumValuesCount: Option[Int] = None,
  inferenceMinAvgValuesPerEnum: Option[Double] = None,
  booleanIncludeNumbers: Boolean = false,
  saveBatchSize: Option[Int] = None,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  setting: Option[DataSetSetting] = None,
  dataView: Option[DataView] = None,
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

case class SynapseDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  tableId: String,
  downloadColumnFiles: Boolean,
  batchSize: Option[Int] = None,
  bulkDownloadGroupNumber: Option[Int] = None,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  setting: Option[DataSetSetting] = None,
  dataView: Option[DataView] = None,
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

case class TranSmartDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  dataPath: Option[String] = None,
  mappingPath: Option[String] = None,
  charsetName: Option[String] = None,
  matchQuotes: Boolean,
  inferFieldTypes: Boolean,
  inferenceMaxEnumValuesCount: Option[Int] = None,
  inferenceMinAvgValuesPerEnum: Option[Double] = None,
  saveBatchSize: Option[Int] = None,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  setting: Option[DataSetSetting] = None,
  dataView: Option[DataView] = None,
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

case class RedCapDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  url: String,
  token: String,
  importDictionaryFlag: Boolean,
  eventNames: Seq[String] = Nil,
  categoriesToInheritFromFirstVisit: Seq[String] = Nil,
  saveBatchSize: Option[Int] = None,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  setting: Option[DataSetSetting] = None,
  dataView: Option[DataView] = None,
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

case class EGaitDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  importRawData: Boolean = false,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  setting: Option[DataSetSetting] = None,
  dataView: Option[DataView] = None,
  timeCreated: Date = new Date(),
  var timeLastExecuted: Option[Date] = None
) extends DataSetImport

object DataSetImportFormattersAndIds {
  implicit val scheduleTimeFormat = Json.format[ScheduledTime]
  implicit val dataSetSettingFormat = DataSetFormattersAndIds.dataSetSettingFormat
  implicit val dataViewFormat = DataView.dataViewFormat

  implicit val dataSetImportFormat: Format[DataSetImport] = new SubTypeFormat[DataSetImport](
    Seq(
      ManifestedFormat(Json.format[CsvDataSetImport]),
      ManifestedFormat(Json.format[JsonDataSetImport]),
      ManifestedFormat(Json.format[SynapseDataSetImport]),
      ManifestedFormat(Json.format[TranSmartDataSetImport]),
      ManifestedFormat(Json.format[RedCapDataSetImport]),
      ManifestedFormat(Json.format[EGaitDataSetImport])
    )
  )

  implicit object DataSetImportIdentity extends BSONObjectIdentity[DataSetImport] {
    def of(entity: DataSetImport): Option[BSONObjectID] = entity._id

    protected def set(entity: DataSetImport, id: Option[BSONObjectID]) =
      entity match {
        case x: CsvDataSetImport => x.copy(_id = id)
        case x: JsonDataSetImport => x.copy(_id = id)
        case x: SynapseDataSetImport => x.copy(_id = id)
        case x: TranSmartDataSetImport => x.copy(_id = id)
        case x: RedCapDataSetImport => x.copy(_id = id)
        case x: EGaitDataSetImport => x.copy(_id = id)
      }
  }

  def copyWithoutTimestamps(entity: DataSetImport) =
    entity match {
      case x: CsvDataSetImport => x.copy(timeCreated = new Date(), timeLastExecuted = None)
      case x: JsonDataSetImport => x.copy(timeCreated = new Date(), timeLastExecuted = None)
      case x: SynapseDataSetImport => x.copy(timeCreated = new Date(), timeLastExecuted = None)
      case x: TranSmartDataSetImport => x.copy(timeCreated = new Date(), timeLastExecuted = None)
      case x: RedCapDataSetImport => x.copy(timeCreated = new Date(), timeLastExecuted = None)
      case x: EGaitDataSetImport => x.copy(timeCreated = new Date(), timeLastExecuted = None)
    }
}