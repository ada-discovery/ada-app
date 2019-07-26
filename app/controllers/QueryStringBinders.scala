package controllers

import models.{Role}
import org.ada.web.controllers.EnumStringBindable
import models.{BatchRequestState, RequestAction}

object QueryStringBinders extends scala.AnyRef {
  implicit val batchRequestStateStringBinder = new EnumStringBindable(BatchRequestState)
  implicit val requestActionStringBinder = new EnumStringBindable(RequestAction)
  implicit val roleStringBinder = new EnumStringBindable(Role)
}
