package controllers.dataset

import controllers.{CrudController, ReadonlyController}
import dataaccess.{Criterion, Sort}
import models.{FieldTypeId, FilterCondition, TablePage}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait DataSetController extends ReadonlyController[BSONObjectID] {

  type FilterOrId = Either[Seq[FilterCondition], BSONObjectID]

  def overviewFieldTypes: Action[AnyContent]

  /**
    * Renders the view with a given id
    *
    * @param dataViewId
    * @param tablePages
    * @param filterOrIds
    * @return
    */
  def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[TablePage],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ): Action[AnyContent]

  def getDefaultView: Action[AnyContent]

  def getDistribution(
    fieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getScatterStats(
    xFieldName: Option[String],
    yFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getDateCount(
    dateFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getCorrelations(
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getFieldNames: Action[AnyContent]

  def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ): Action[AnyContent]

  def getFieldValue(id: BSONObjectID, fieldName: String): Action[AnyContent]

  def exportRecordsAsCsv(
    dataViewId: BSONObjectID,
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]

  def exportRecordsAsJson(
    dataViewId: BSONObjectID,
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]

  def exportTranSMARTDataFile(delimiter : String): Action[AnyContent]

  def exportTranSMARTMappingFile(delimiter : String): Action[AnyContent]

  def findCustom(
    filterOrId: Either[Seq[FilterCondition], BSONObjectID],
    orderBy: String,
    projection: Seq[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Action[AnyContent]
}