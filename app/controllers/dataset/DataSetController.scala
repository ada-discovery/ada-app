package controllers.dataset

import controllers.{ReadonlyController, CrudController}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import util.FilterSpec

trait DataSetController extends ReadonlyController[BSONObjectID] {

  def exportAllRecordsAsCsv(delimiter : String): Action[AnyContent]

  def exportAllRecordsAsJson: Action[AnyContent]

  def exportRecordsAsCsv(delimiter : String, filter: FilterSpec): Action[AnyContent]

  def exportRecordsAsJson(filter: FilterSpec): Action[AnyContent]

  def overviewFieldTypes: Action[AnyContent]

  def overview: Action[AnyContent]

  def overviewList(page: Int, orderBy: String, filter: FilterSpec): Action[AnyContent]

  def getScatterStats(xFieldName: Option[String], yFieldName: Option[String], filter: FilterSpec): Action[AnyContent]

  def getDistribution(fieldName: Option[String], filter: FilterSpec): Action[AnyContent]

  def exportTranSMARTDataFile(delimiter : String): Action[AnyContent]

  def exportTranSMARTMappingFile(delimiter : String): Action[AnyContent]

  def getFieldNames: Action[AnyContent]
}
