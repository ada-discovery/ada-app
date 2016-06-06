package controllers.dataset

import controllers.CrudController
import play.api.mvc.{Action, AnyContent}
import util.FilterSpec

trait DictionaryController extends CrudController[String] {

  def overviewList(page: Int, orderBy: String, filter: FilterSpec): Action[AnyContent]

  def inferDictionary: Action[AnyContent]
}