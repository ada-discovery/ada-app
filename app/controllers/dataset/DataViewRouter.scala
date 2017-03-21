package controllers.dataset

import controllers.{GenericJsRouter, GenericRouter}
import scalaz.Scalaz._

final class DataViewRouter(dataSetId: String) extends GenericRouter(routes.DataViewDispatcher, "dataSet", dataSetId) {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val create = routeFun(_.create)
  val get = routes.get _ map route
  val save = routeFun(_.save)
  val update = routes.update _ map route
  val delete = routes.delete _ map route
  val copy = routes.copy _ map route
  val idAndNames = routeFun(_.idAndNames)
  val getAndShowView = routes.getAndShowView _ map route
  val updateAndShowView = routes.updateAndShowView _ map route
}

final class DataViewJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.DataViewDispatcher, "dataSet", dataSetId) {
  val addDistributions = routeFun(_.addDistributions)
  val addDistribution = routeFun(_.addDistribution)
  val addBoxPlots = routeFun(_.addBoxPlots)
  val addScatter = routeFun(_.addScatter)
  val addCorrelation = routeFun(_.addCorrelation)
  val addTableFields = routeFun(_.addTableFields)
  val saveFilter = routeFun(_.saveFilter)
}