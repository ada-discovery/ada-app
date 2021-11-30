package org.ada.server.models

import play.api.libs.json.{Json, OWrites, Reads}

case class DataSetInfo(
  dataSetType: DataSetType.Value,
  dataSetJoinIdName: String
  )

object DataSetInfo {
  implicit val dataSetInfoRead: Reads[DataSetInfo] = Json.reads[DataSetInfo]
  implicit val dataSetInfoWrite: OWrites[DataSetInfo] = Json.writes[DataSetInfo]
}




