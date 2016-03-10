package controllers.dataset

import controllers.GenericRouter

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
  val exportAllCsv = routeFun(_.exportAllRecordsAsCsv())
  val exportAllJson = routeFun(_.exportAllRecordsAsJson)
  val exportCsv = routeFun(_.exportRecordsAsCsv())
  val exportJson  = routeFun(_.exportRecordsAsJson())
  val exportTranSMARTData = routeFun(_.exportTranSMARTDataFile())
  val exportTranSMARTMapping = routeFun(_.exportTranSMARTMappingFile())
  val getScatterStats = routeFun(_.getScatterStats())
  val getDistribution = routeFun(_.getDistribution())
  val fieldNames = routeFun(_.getFieldNames)
}