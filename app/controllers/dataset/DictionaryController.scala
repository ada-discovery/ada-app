package controllers.dataset

import controllers.CrudController
import play.api.mvc.{Action, AnyContent}
import util.FilterCondition

trait DictionaryController extends CrudController[String] {

  def overviewList(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent]

  def inferDictionary: Action[AnyContent]

  def updateLabel(id: String, label: String): Action[AnyContent]

  def jsRoutes: Action[AnyContent]

  def exportAllRecordsAsCsv(delimiter : String): Action[AnyContent]

  def exportAllRecordsAsJson: Action[AnyContent]

  def exportRecordsAsCsv(delimiter : String, filter: Seq[FilterCondition]): Action[AnyContent]

  def exportRecordsAsJson(filter: Seq[FilterCondition]): Action[AnyContent]
}