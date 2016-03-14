package controllers.dataset

import controllers.{GenericRouter, GenericJsRouter}
import play.api.routing.JavaScriptReverseRoute

import scalaz.Scalaz._

final class CategoryRouter(dataSetId: String) extends GenericRouter(routes.CategoryDispatcher, "dataSet", dataSetId) {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val create = routeFun(_.create)
  val get = routes.get _ map route
  val save = routeFun(_.save)
  val update = routes.update _ map route
  val delete = routes.delete _ map route
  val getCategoryD3Root= routeFun(_.getCategoryD3Root)
  val jsRoutes = routeFun(_.jsRoutes)
}

final class CategoryJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.CategoryDispatcher, "dataSet", dataSetId) {
  val get = routeFun(_.get)
}