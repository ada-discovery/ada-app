package controllers

import models.{BatchRequestState, RequestAction, Role}
import org.ada.web.controllers.EnumStringBindable

object QueryStringBinders extends scala.AnyRef {
  implicit val batchRequestStateStringBinder = new EnumStringBindable(BatchRequestState)
  implicit val requestActionStringBinder = new EnumStringBindable(RequestAction)
  implicit val roleStringBinder = new EnumStringBindable(Role)
}
