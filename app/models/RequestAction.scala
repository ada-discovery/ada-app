package models

import org.ada.web.controllers.EnumStringBindable


object RequestAction extends Enumeration {
  val Approve, Reject = Value
  implicit val requestActionStringBinder = new EnumStringBindable(RequestAction)
}