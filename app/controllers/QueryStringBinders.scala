package controllers

import controllers.mpower.AggFunction
import org.ada.web.controllers.EnumStringBindable

object QueryStringBinders {
  implicit val aggFunctionQueryStringBinder = new EnumStringBindable(AggFunction)
}