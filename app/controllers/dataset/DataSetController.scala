package controllers.dataset

import models.Filter.FilterOrId
import models.ml.VectorTransformType
import models.{AggType, FieldTypeId}
import org.incal.play.PageOrder
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import org.incal.core.FilterCondition
import org.incal.play.controllers.ReadonlyController
import org.incal.play.security.AuthAction

trait DataSetController extends ReadonlyController[BSONObjectID] {

  def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[PageOrder],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ): Action[AnyContent]

  def getDefaultView: Action[AnyContent]

  def getViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    filterOrId: FilterOrId,
    oldCountDiff: Option[Int]
  ): Action[AnyContent]

  def getNewFilterViewElementsAndWidgetsCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    totalCount: Int
  ): Action[AnyContent]

  def getTableScreen(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getTable(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getWidgets: Action[AnyContent]

  def getDistribution(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcDistribution(
    fieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getCumulativeCount(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcCumulativeCount(
    fieldName: String,
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getScatter(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcScatter(
    xFieldName: String,
    yFieldName: String,
    groupOrValueFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getCorrelations(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcCorrelations(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getHeatmap(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def calcHeatmap(
    xFieldName: String,
    yFieldName: String,
    valueFieldName: Option[String],
    aggType: Option[AggType.Value],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getIndependenceTest(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getIndependenceTestForViewFilters: Action[AnyContent]

  def testIndependence(
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def testIndependenceForViewFilters(
    viewId: BSONObjectID
  ): Action[AnyContent]

  def getFractalis(
    fieldName: Option[String]
  ): Action[AnyContent]

  def getClusterization: Action[AnyContent]

  def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int]
  ): Action[AnyContent]

  def getSeriesProcessingSpec: Action[AnyContent]

  def runSeriesProcessing: Action[AnyContent]

  def getSeriesTransformationSpec: Action[AnyContent]

  def runSeriesTransformation: Action[AnyContent]

  def getFieldNamesAndLabels(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ): Action[AnyContent]

  def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ): Action[AnyContent]

  def getFieldValue(id: BSONObjectID, fieldName: String): Action[AnyContent]

  def getField(fieldName: String): Action[AnyContent]

  def getFieldTypeWithAllowedValues(fieldName: String): Action[AnyContent]

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

  def getCategoriesWithFieldsAsTreeNodes(filterOrId: FilterOrId): Action[AnyContent]

  def findCustom(
    filterOrId: FilterOrId,
    orderBy: String,
    projection: Seq[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Action[AnyContent]
}