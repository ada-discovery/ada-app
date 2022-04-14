package org.ada.server.models

import play.api.libs.json.{Reads, Writes}

object DataSetType extends Enumeration {
  val omics = Value("Omics")

  implicit val dataSetTypeRead: Reads[DataSetType.Value] = Reads.enumNameReads(DataSetType)
  implicit val dataSetTypeWrite: Writes[DataSetType.Value] = Writes.enumNameWrites

}
