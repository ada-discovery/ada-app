package controllers.dataset

import controllers.CrudController
import models.FilterCondition
import play.api.mvc.{Action, AnyContent}

trait DictionaryController extends CrudController[String] {

  def overviewList(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent]

  def inferDictionary: Action[AnyContent]

  def updateLabel(id: String, label: String): Action[AnyContent]

  def jsRoutes: Action[AnyContent]

  def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]

  def exportRecordsAsJson(filter: Seq[FilterCondition], tableColumnsOnly: Boolean): Action[AnyContent]
}