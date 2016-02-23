package controllers

import controllers.denopa.routes
import play.api.mvc.Call
import reactivemongo.bson.BSONObjectID
import util.FilterSpec


/**
  * Container for various calls from Controllers.
  * To be passed to other modules like views to simplify data access.
  * @param list
  * @param plainList
  * @param get
  * @param exportAllCsv
  * @param exportAllJson
  * @param exportCsv
  * @param exportJson
  * @param exportTranSMARTData
  * @param exportTranSMARTMapping
  */
case class DataSetRouter(
  list: (Int, String, FilterSpec) => Call,
  plainList: Call,
  overviewList: (Int, String, FilterSpec) => Call,
  plainOverviewList: Call,
  get: BSONObjectID => Call,
  exportAllCsv: Call,
  exportAllJson: Call,
  exportCsv: Call,
  exportJson: Call,
  exportTranSMARTData: Call,
  exportTranSMARTMapping: Call,
  getScatterStats: Call,
  getDistribution: Call,
  fieldNames: Call
)

object DataSetRouter {
  def apply(dataSetId: String): DataSetRouter = {

    def route(call: Call) = {
      val delimiter = if (call.url.contains("?")) "&" else "?"
      call.copy(url = call.url + delimiter + "dataSet=" + dataSetId)
    }

    DataSetRouter(
      (a: Int, b: String, c: FilterSpec) => route(routes.DataSetDispatcher.find(a, b, c)),
      route(routes.DataSetDispatcher.find()),
      (a: Int, b: String, c: FilterSpec) => route(routes.DataSetDispatcher.overviewList(a, b, c)),
      route(routes.DataSetDispatcher.overviewList()),
      (id: BSONObjectID) => route(routes.DataSetDispatcher.get(id)),
      route(routes.DataSetDispatcher.exportAllRecordsAsCsv()),
      route(routes.DataSetDispatcher.exportAllRecordsAsJson()),
      route(routes.DataSetDispatcher.exportRecordsAsCsv()),
      route(routes.DataSetDispatcher.exportRecordsAsJson()),
      route(routes.DataSetDispatcher.exportTranSMARTDataFile()),
      route(routes.DataSetDispatcher.exportTranSMARTMappingFile()),
      route(routes.DataSetDispatcher.getScatterStats()),
      route(routes.DataSetDispatcher.getDistribution()),
      route(routes.DataSetDispatcher.getFieldNames())
    )
  }
}