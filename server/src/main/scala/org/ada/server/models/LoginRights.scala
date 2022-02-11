package org.ada.server.models

import play.api.libs.json.{Reads, Writes}

object LoginRights extends Enumeration {
  val viewOnly, standard = Value

  implicit val loginRightsRead: Reads[LoginRights.Value] = Reads.enumNameReads(LoginRights)
  implicit val loginRightsWrite: Writes[LoginRights.Value] = Writes.enumNameWrites
}
