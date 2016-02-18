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
  * @param save
  * @param update
  */
case class DictionaryRouter(
  list: (Int, String, FilterSpec) => Call,
  plainList: Call,
  get: String => Call,
  save: Call,
  update: String => Call
)