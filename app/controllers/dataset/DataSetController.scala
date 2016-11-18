package controllers.dataset

import controllers.{ReadonlyController, CrudController}
import models.FieldTypeId
import models.FilterCondition
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait DataSetController extends ReadonlyController[BSONObjectID] {

  def overviewFieldTypes: Action[AnyContent]

  def overviewList(
    page: Int,
    orderBy: String,
    filterOrId: Either[Seq[models.FilterCondition], BSONObjectID]
  ): Action[AnyContent]

  def getDistribution(
    fieldName: Option[String],
    filterOrId: Either[Seq[models.FilterCondition], BSONObjectID]
  ): Action[AnyContent]

  def getScatterStats(
    xFieldName: Option[String],
    yFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: Either[Seq[models.FilterCondition], BSONObjectID]
  ): Action[AnyContent]

  def getDateCount(
    dateFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: Either[Seq[models.FilterCondition], BSONObjectID]
  ): Action[AnyContent]

  def getCorrelations(
    fieldNames: Seq[String],
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ): Action[AnyContent]

  def getFieldNames: Action[AnyContent]

  def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ): Action[AnyContent]

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