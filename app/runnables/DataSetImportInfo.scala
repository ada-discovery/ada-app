package runnables

import models.DataSetSetting

case class DataSetImportInfo(
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: String,
  delimiter: String,
  eol: Option[String] = None,
  setting: Option[DataSetSetting] = None
)
