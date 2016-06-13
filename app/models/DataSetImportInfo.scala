package models

import java.io.File

import controllers.{ManifestedFormat, SubTypeFormat}
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import play.api.libs.json._

abstract class DataSetImportInfo {
  val _id: Option[BSONObjectID]
  val dataSpaceName: String
  val dataSetId: String
  val dataSetName: String
  val setting: Option[DataSetSetting]
}

case class CsvDataSetImportInfo(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: Option[String],
  delimiter: String,
  eol: Option[String],
  charsetName: Option[String],
  setting: Option[DataSetSetting]
) extends DataSetImportInfo

case class SynapseDataSetImportInfo(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  tableId: String,
  setting: Option[DataSetSetting]
) extends DataSetImportInfo

case class TranSmartDataSetImportInfo(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  dataPath: Option[String],
  mappingPath: Option[String],
  charsetName: Option[String],
  setting: Option[DataSetSetting]
) extends DataSetImportInfo

case class RedCapImportInfo(
  _id: Option[BSONObjectID],
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  url: String,
  token: String,
  setting: Option[DataSetSetting]
) extends DataSetImportInfo

object DataSetImportInfoFormattersAndIds {
  implicit val dataSetSettingFormat = DataSetFormattersAndIds.dataSetSettingFormat

  implicit val dataSetImportInfoFormat: Format[DataSetImportInfo] = new SubTypeFormat[DataSetImportInfo](
    Seq(
      ManifestedFormat(Json.format[CsvDataSetImportInfo]),
      ManifestedFormat(Json.format[SynapseDataSetImportInfo]),
      ManifestedFormat(Json.format[TranSmartDataSetImportInfo]),
      ManifestedFormat(Json.format[RedCapImportInfo])
    )
  )

  implicit object DataSetImportInfoIdentity extends BSONObjectIdentity[DataSetImportInfo] {
    def of(entity: DataSetImportInfo): Option[BSONObjectID] = entity._id
    protected def set(entity: DataSetImportInfo, id: Option[BSONObjectID]) =
      entity match {
        case x: CsvDataSetImportInfo => x.copy(_id = id)
        case x: SynapseDataSetImportInfo => x.copy(_id = id)
        case x: TranSmartDataSetImportInfo => x.copy(_id = id)
        case x: RedCapImportInfo => x.copy(_id = id)
      }
  }
}