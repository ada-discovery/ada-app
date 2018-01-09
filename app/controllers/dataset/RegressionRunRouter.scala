package controllers.dataset

import controllers.{GenericJsRouter, GenericRouter}

import scalaz.Scalaz._

class RegressionRunRouter(dataSetId: String) extends GenericRouter(routes.RegressionRunDispatcher, "dataSet", dataSetId) {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
  val delete = routes.delete _ map route
}

final class RegressionRunJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.RegressionRunDispatcher, "dataSet", dataSetId) {
  val regress = routeFun(_.regress)
}