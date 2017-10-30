package controllers.dataset

import controllers.core.{CrudController, ReadonlyController}
import models.FilterCondition.FilterOrId
import models.ml.VectorTransformType
import models.{FieldTypeId, FilterCondition, PageOrder}
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import views.html.dataset

import scala.concurrent.Future

trait DataSetController extends ReadonlyController[BSONObjectID] {

  def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[PageOrder],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ): Action[AnyContent]

  def getDefaultView: Action[AnyContent]

  def getWidgetPanelAndTable(
    dataViewId: BSONObjectID,
    tablePage: Int,
    tableOrder: String,
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getTable(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getWidgets(
    callbackId: String
  ): Action[AnyContent]

  def getDistribution(
    fieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getCumulativeCount(
    fieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getScatterStats(
    xFieldName: Option[String],
    yFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getCorrelations(
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Action[AnyContent]

  def getFractalis(
    fieldName: Option[String]
  ): Action[AnyContent]

  def getRegression: Action[AnyContent]

  def getClusterization: Action[AnyContent]

  def getIndependenceTest: Action[AnyContent]

  def regress(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int],
    trainingTestingSplit: Option[Double],
    repetitions: Option[Int],
    crossValidationFolds: Option[Int]
  ): Action[AnyContent]

  def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int]
  ): Action[AnyContent]

  def testIndependence(
    targetFieldName: String,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID]
  ): Action[AnyContent]

  def getSeriesProcessingSpec: Action[AnyContent]

  def runSeriesProcessing: Action[AnyContent]

  def getSeriesTransformationSpec: Action[AnyContent]

  def runSeriesTransformation: Action[AnyContent]

  def getFieldNames: Action[AnyContent]

  def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ): Action[AnyContent]

  def getFieldValue(id: BSONObjectID, fieldName: String): Action[AnyContent]

  def getField(fieldName: String): Action[AnyContent]

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