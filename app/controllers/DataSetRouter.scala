package controllers

import play.api.mvc.Call
import reactivemongo.bson.BSONObjectID


/**
  * Container for various calls from Controllers.
  * To be passed to other modules like views to simplify data access.
  * @param findCall
  * @param plainFindCall
  * @param getCall
  * @param exportCsvCall
  * @param exportJsonCall
  * @param exportTranSMARTDataCall
  * @param exportTranSMARTMappingCall
  * @param getScatterStatsCall
  */
case class DataSetRouter(
  findCall : (Int, String, String) => Call,
  plainFindCall : Call,
  getCall : BSONObjectID => Call,
  exportCsvCall : Call,
  exportJsonCall : Call,
  exportTranSMARTDataCall : Call,
  exportTranSMARTMappingCall : Call,
  getScatterStatsCall : Call
)
