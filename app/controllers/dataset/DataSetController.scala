package controllers.dataset

import controllers.{ReadonlyController, CrudController}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import util.Criteria

trait DataSetController extends ReadonlyController[BSONObjectID] {

  def exportAllRecordsAsCsv(delimiter : String): Action[AnyContent]

  def exportAllRecordsAsJson: Action[AnyContent]

  def exportRecordsAsCsv(delimiter : String, filter: Criteria): Action[AnyContent]

  def exportRecordsAsJson(filter: Criteria): Action[AnyContent]

  def overviewFieldTypes: Action[AnyContent]

  def overviewList(page: Int, orderBy: String, filter: Criteria): Action[AnyContent]

  def getScatterStats(xFieldName: Option[String], yFieldName: Option[String], filter: Criteria): Action[AnyContent]

  def getDistribution(fieldName: Option[String], filter: Criteria): Action[AnyContent]

  def exportTranSMARTDataFile(delimiter : String): Action[AnyContent]

  def exportTranSMARTMappingFile(delimiter : String): Action[AnyContent]

  def getFieldNames: Action[AnyContent]

  def getFieldValue(id: BSONObjectID, fieldName: String): Action[AnyContent]
}
