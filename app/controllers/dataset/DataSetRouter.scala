package controllers.dataset

import controllers.{GenericJsRouter, GenericRouter}

import scalaz.Scalaz._

/**
  * Container for various calls from Controllers.
  * To be passed to other modules like views to simplify data access.
  */
class DataSetRouter(dataSetId: String) extends GenericRouter(routes.DataSetDispatcher, "dataSet", dataSetId) {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val overviewList = routes.overviewList _ map route
  val plainOverviewList = routeFun(_.overviewList())
  val get = routes.get _ map route
  val plainGetScatterStats = routeFun(_.getScatterStats())
  val getScatterStats = routes.getScatterStats _ map route
  val plainGetDistribution = routeFun(_.getDistribution())
  val getDistribution = routes.getDistribution _ map route
  val getDateCount = routes.getDateCount _ map route
  val fields = routes.getFields _ map route
  val allFields = routeFun(_.getFields())
  val fieldNames = routeFun(_.getFieldNames)
  val getFieldValue = routes.getFieldValue _ map route
  val exportCsv = routes.exportRecordsAsCsv _ map route
  val exportJson  = routes.exportRecordsAsJson _ map route
  val exportTranSMARTData = routeFun(_.exportTranSMARTDataFile())
  val exportTranSMARTMapping = routeFun(_.exportTranSMARTMappingFile())
}

final class DataSetJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.DataSetDispatcher, "dataSet", dataSetId) {
  val getFieldValue = routeFun(_.getFieldValue)
}