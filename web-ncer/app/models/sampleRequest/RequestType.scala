package models.sampleRequest

import play.api.libs.json.{Reads, Writes}


object RequestType extends Enumeration {
  val Data, Images, Material = Value

  implicit val requestTypeRead: Reads[RequestType.Value] = Reads.enumNameReads(RequestType)
  implicit val requestTypeWrite: Writes[RequestType.Value] = Writes.enumNameWrites
}
