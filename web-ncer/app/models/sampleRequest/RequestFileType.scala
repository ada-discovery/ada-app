package models.sampleRequest

import play.api.libs.json.{Reads, Writes}

object RequestFileType extends Enumeration {
  val METC_LETTER,
  ORG_CONDITIONS,
  MTA,
  DTA,
  OTHER,
  NONE = Value

  implicit val requestFileTypeRead: Reads[RequestFileType.Value] = Reads.enumNameReads(RequestFileType)
  implicit val requestFileTypeWrite: Writes[RequestFileType.Value] = Writes.enumNameWrites

}
