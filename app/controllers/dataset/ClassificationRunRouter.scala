package controllers.dataset

import controllers.{GenericJsRouter, GenericRouter}

import scalaz.Scalaz._

class ClassificationRunRouter(dataSetId: String) extends GenericRouter(routes.ClassificationRunDispatcher, "dataSet", dataSetId) {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
}

final class ClassificationRunJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.ClassificationRunDispatcher, "dataSet", dataSetId) {
  val classify = routeFun(_.classify)
  val selectFeaturesAsChiSquare = routeFun(_.selectFeaturesAsChiSquare)
}
