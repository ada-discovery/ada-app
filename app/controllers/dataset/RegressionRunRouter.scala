package controllers.dataset

import org.incal.play.controllers.{GenericJsRouter, GenericRouter}

import scalaz.Scalaz._

class RegressionRunRouter(dataSetId: String) extends GenericRouter(routes.RegressionRunDispatcher, "dataSet", dataSetId) {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
  val delete = routes.delete _ map route
  val exportToDataSet = routes.exportToDataSet _ map route
  val exportCsv = routes.exportRecordsAsCsv _ map route
  val exportJson  = routes.exportRecordsAsJson _ map route
}

final class RegressionRunJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.RegressionRunDispatcher, "dataSet", dataSetId) {
  val regress = routeFun(_.regress)
}