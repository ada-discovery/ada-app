package controllers

import play.api.mvc.Call
import reactivemongo.bson.BSONObjectID
import util.FilterSpec


/**
  * Container for various calls from Controllers.
  * To be passed to other modules like views to simplify data access.
  * @param findCall
  * @param plainFindCall
  * @param getCall
  * @param exportAllCsvCall
  * @param exportAllJsonCall
  * @param exportCsvCall
  * @param exportJsonCall
  * @param exportTranSMARTDataCall
  * @param exportTranSMARTMappingCall
  * @param getScatterStatsCall
  */
case class DataSetRouter(
  findCall : (Int, String, FilterSpec) => Call,
  plainFindCall : Call,
  overviewListCall : (Int, String, FilterSpec) => Call,
  plainOverviewListCall : Call,
  getCall : BSONObjectID => Call,
  exportAllCsvCall : Call,
  exportAllJsonCall : Call,
  exportCsvCall : Call,
  exportJsonCall : Call,
  exportTranSMARTDataCall : Call,
  exportTranSMARTMappingCall : Call,
  getScatterStatsCall : Call
)
