package models

import java.io.File

abstract class DataSetImportInfo(
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  setting: Option[DataSetSetting]
)

case class CsvDataSetImportInfo(
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: Option[String],
  file: Option[File],
  delimiter: String,
  eol: Option[String],
  charsetName: Option[String],
  setting: Option[DataSetSetting]
) extends DataSetImportInfo(dataSpaceName, dataSetId, dataSetName, setting)

case class TranSmartImportInfo(
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  dataPath: Option[String],
  dataFile: Option[File],
  mappingPath: Option[String],
  mappingFile: Option[File],
  charsetName: Option[String],
  setting: Option[DataSetSetting]
) extends DataSetImportInfo(dataSpaceName, dataSetId, dataSetName, setting)

case class RedCapImportInfo(
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  url: String,
  token: String,
  setting: Option[DataSetSetting]
) extends DataSetImportInfo(dataSpaceName, dataSetId, dataSetName, setting)


// handy constructors... could be moved to data upload controller


object CsvDataSetImportInfo {
  def apply(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    path: Option[String],
    delimiter: String,
    eol: Option[String],
    charsetName: Option[String],
    setting: Option[DataSetSetting]
  ) = new CsvDataSetImportInfo(dataSpaceName, dataSetId, dataSetName, path, None, delimiter, eol, charsetName, setting)
}

object TranSmartImportInfo {
  def apply(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    dataPath: Option[String],
    mappingPath: Option[String],
    charsetName: Option[String],
    setting: Option[DataSetSetting]
  ) = new TranSmartImportInfo(dataSpaceName, dataSetId, dataSetName, dataPath, None, mappingPath, None, charsetName, setting)
}