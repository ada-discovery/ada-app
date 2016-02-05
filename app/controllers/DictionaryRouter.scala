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
  */
case class DictionaryRouter(
  findCall : (Int, String, FilterSpec) => Call,
  plainFindCall : Call,
  getCall : String => Call
)