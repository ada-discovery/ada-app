package org.ada.web.controllers.dataset

import org.incal.core.FilterCondition
import org.incal.play.controllers.CrudController
import play.api.libs.Files
import play.api.mvc.{Action, AnyContent, MultipartFormData}

trait DictionaryController extends CrudController[String] {

  def updateLabel(id: String, label: String): Action[AnyContent]

  def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]

  def exportRecordsAsJson(filter: Seq[FilterCondition], tableColumnsOnly: Boolean): Action[AnyContent]

  def setDefaultLabels: Action[AnyContent]

  def convertLabelsToCamelCase: Action[AnyContent]

  def updatesLabelsByFile(fileName: String): Action[AnyContent]
}