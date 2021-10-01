package models.sampleRequest

import play.api.libs.json.{Reads, Writes}

object OverviewStatus extends Enumeration {
  val All,
  Draft,
  Validation,
  Review,
  Revision,
  Approved,
  Delivery,
  Delivered,
  Partially_Delivered,
  Cancelled,
  Rejected,
  Closed_Approved,
  None = Value

  implicit val overviewStatusRead: Reads[OverviewStatus.Value] = Reads.enumNameReads(OverviewStatus)
  implicit val overviewStatusWrite: Writes[OverviewStatus.Value] = Writes.enumNameWrites
}
