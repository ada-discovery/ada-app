package models

import java.io.File

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

  val csvDataSetImportInfoFormat = Json.format[CsvDataSetImportInfo]
  val synapseDataSetImportInfoFormat = Json.format[SynapseDataSetImportInfo]
  val tranSmartDataSetImportInfoFormat = Json.format[TranSmartDataSetImportInfo]
  val redCapImportInfoFormat = Json.format[RedCapImportInfo]

  implicit val dataSetImportInfoFormat: Format[DataSetImportInfo] = new Format[DataSetImportInfo] {
    override def reads(json: JsValue): JsResult[DataSetImportInfo] = {
      val concreteType = (json \ "concreteType").get.as[String]
      if (concreteType == classOf[CsvDataSetImportInfo].getName) {
        csvDataSetImportInfoFormat.reads(json)
      } else if (concreteType == classOf[SynapseDataSetImportInfo].getName) {
        synapseDataSetImportInfoFormat.reads(json)
      } else if (concreteType == classOf[TranSmartDataSetImportInfo].getName) {
        tranSmartDataSetImportInfoFormat.reads(json)
      } else if (concreteType == classOf[RedCapImportInfo].getName) {
        redCapImportInfoFormat.reads(json)
      } else
        throw new AdaException(s"DataSetImportInfo  type '$concreteType' not recognized.")
    }

    override def writes(o: DataSetImportInfo): JsValue = {
      o match {
        case x: CsvDataSetImportInfo => csvDataSetImportInfoFormat.writes(x).asInstanceOf[JsObject] + ("concreteType", JsString(classOf[CsvDataSetImportInfo].getName))
        case x: SynapseDataSetImportInfo => synapseDataSetImportInfoFormat.writes(x).asInstanceOf[JsObject] + ("concreteType", JsString(classOf[CsvDataSetImportInfo].getName))
        case x: TranSmartDataSetImportInfo => tranSmartDataSetImportInfoFormat.writes(x).asInstanceOf[JsObject] + ("concreteType", JsString(classOf[CsvDataSetImportInfo].getName))
        case x: RedCapImportInfo => redCapImportInfoFormat.writes(x).asInstanceOf[JsObject] + ("concreteType", JsString(classOf[CsvDataSetImportInfo].getName))
      }
    }
  }

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