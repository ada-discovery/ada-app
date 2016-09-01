package controllers.dataset

import controllers.{ReadonlyController, CrudController}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import util.FilterCondition

trait DataSetController extends ReadonlyController[BSONObjectID] {

  def overviewFieldTypes: Action[AnyContent]

  def overviewList(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent]

  def getScatterStats(
    xFieldName: Option[String],
    yFieldName: Option[String],
    zFieldName: Option[String],
    filter: Seq[FilterCondition]
  ): Action[AnyContent]

  def getDistribution(fieldName: Option[String], filter: Seq[FilterCondition]): Action[AnyContent]

  def getFieldNames: Action[AnyContent]

  def getFieldValue(id: BSONObjectID, fieldName: String): Action[AnyContent]

  def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]

  def exportRecordsAsJson(filter: Seq[FilterCondition], tableColumnsOnly: Boolean): Action[AnyContent]

  def exportTranSMARTDataFile(delimiter : String): Action[AnyContent]

  def exportTranSMARTMappingFile(delimiter : String): Action[AnyContent]
}
