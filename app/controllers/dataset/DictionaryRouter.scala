package controllers.dataset

import controllers.GenericRouter

import scalaz.Scalaz._

/**
  * Container for various calls from Controllers.
  * To be passed to other modules like views to simplify data access.
  */
class DictionaryRouter(dataSetId: String) extends GenericRouter(routes.DictionaryDispatcher, "dataSet", dataSetId) {
  val list = routes.overviewList _ map route
  val plainList =  routeFun(_.overviewList())
  val get = routes.get _ map route
  val save = routeFun(_.save)
  val update = routes.update _ map route
  val inferDictionary = routeFun(_.inferDictionary)
}