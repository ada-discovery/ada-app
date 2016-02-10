package controllers

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