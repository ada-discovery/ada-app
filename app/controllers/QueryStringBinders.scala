package controllers

import models.{BatchRequestState, RequestAction}
import org.incal.play.formatters.EnumStringBindable

object QueryStringBinders extends scala.AnyRef {
  implicit val batchRequestStateStringBinder = new EnumStringBindable(BatchRequestState)
  implicit val requestActionStringBinder = new EnumStringBindable(RequestAction)
}
