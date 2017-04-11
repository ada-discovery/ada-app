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
  val saveForName = routes.saveForName _ map route
  val update = routes.update _ map route
  val delete = routes.delete _ map route
  val getCategoryD3Root= routeFun(_.getCategoryD3Root)
  val relocateToParent = routes.relocateToParent _ map route
  val idAndNames = routeFun(_.idAndNames)
  val jsRoutes = routeFun(_.jsRoutes)
}

final class CategoryJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.CategoryDispatcher, "dataSet", dataSetId) {
  val get = routeFun(_.get)
  val saveForName = routeFun(_.saveForName)
  val relocateToParent = routeFun(_.relocateToParent)
  val addFields = routeFun(_.addFields)
  val updateLabel = routeFun(_.updateLabel)
}