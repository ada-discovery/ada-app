package controllers

import play.api.mvc.Call
import reactivemongo.bson.BSONObjectID

case class DataSetRouter(
  findCall : (Int, String, String) => Call,
  plainFindCall : Call,
  getCall : BSONObjectID => Call,
  exportCsvCall : Call,
  exportJsonCall : Call,
  exportTranSMARTDataCall : Call,
  exportTranSMARTMappingCall : Call
)
