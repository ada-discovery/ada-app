package controllers

import models.{BatchRequestState, RequestAction}
import org.ada.web.controllers.EnumStringBindable

object QueryStringBinders extends scala.AnyRef {
  implicit val batchRequestStateStringBinder = new EnumStringBindable(BatchRequestState)
  implicit val requestActionStringBinder = new EnumStringBindable(RequestAction)
}
