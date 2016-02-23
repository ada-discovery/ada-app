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

object DictionaryRouter {
  def apply(dataSetId: String): DictionaryRouter = {

    def route(call: Call) = {
      val delimiter = if (call.url.contains("?")) "&" else "?"
      call.copy(url = call.url + delimiter + "dataSet=" + dataSetId)
    }

    DictionaryRouter(
      (a: Int, b: String, c: FilterSpec) => route(routes.DictionaryDispatcher.overviewList(a, b, c)),
      route(routes.DictionaryDispatcher.overviewList()),
      (id: String) => route(routes.DictionaryDispatcher.get(id)),
      route(routes.DictionaryDispatcher.save),
      (id: String) => route(routes.DictionaryDispatcher.update(id)) // routes.DictionaryDispatcher.update(_).andThen(route)
    )
  }
}