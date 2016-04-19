package runnables

import java.io.File

import models.DataSetSetting

case class DataSetImportInfo(
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: Option[String],
  file: Option[File],
  delimiter: String,
  eol: Option[String],
  charsetName: Option[String],
  setting: Option[DataSetSetting]
) {
  def this(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    path: Option[String],
    delimiter: String,
    eol: Option[String],
    charsetName: Option[String],
    setting: Option[DataSetSetting]
  ) = this(dataSpaceName, dataSetId, dataSetName, path, None, delimiter, eol, charsetName, setting)
}
