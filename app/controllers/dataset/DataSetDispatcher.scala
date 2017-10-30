package controllers.dataset

import javax.inject.Inject

import controllers.{ControllerDispatcher, SecureControllerDispatcher}
import models.FilterCondition.FilterOrId
import models.ml.VectorTransformType
import models.{FieldTypeId, FilterCondition, PageOrder}
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil._

import scala.concurrent.Future

class DataSetDispatcher @Inject() (dscf: DataSetControllerFactory) extends SecureControllerDispatcher[DataSetController]("dataSet") with DataSetController {

  override protected def getController(id: String) =
    dscf(id).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array("admin"))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(createDataSetPermission(controllerId, "dataSet", actionName))

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(
    page: Int,
    orderBy: String,
    filter: Seq[FilterCondition]
  ) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[PageOrder],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ) = dispatch(_.getView(dataViewId, tablePages, filterOrIds, filterChanged))

  override def getDefaultView = dispatch(_.getDefaultView)

  override def getWidgetPanelAndTable(
    dataViewId: BSONObjectID,
    tablePage: Int,
    tableOrder: String,
    filterOrId: FilterOrId
  ) = dispatch(_.getWidgetPanelAndTable(dataViewId, tablePage, tableOrder, filterOrId))

  override def getTable(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ) = dispatch(_.getTable(page, orderBy, fieldNames, filterOrId))

  override def getWidgets(
    callbackId: String
  ) = dispatch(_.getWidgets(callbackId))

  override def getScatterStats(
    xFieldName: Option[String],
    yFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = dispatch(_.getScatterStats(xFieldName, yFieldName, groupFieldName, filterOrId))

  override def getDistribution(
    fieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = dispatch(_.getDistribution(fieldName, groupFieldName, filterOrId))

  override def getCorrelations(
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ) = dispatch(_.getCorrelations(fieldNames, filterOrId))

  override def getCumulativeCount(
    dateFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = dispatch(_.getCumulativeCount(dateFieldName, groupFieldName, filterOrId))

  override def getFractalis(
    fieldNameOption: Option[String]
  ) = dispatch(_.getFractalis(fieldNameOption))

  override def getRegression = dispatch(_.getRegression)

  override def getClusterization = dispatch(_.getClusterization)

  override def getIndependenceTest = dispatch(_.getIndependenceTest)

  override def regress(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int],
    trainingTestingSplit: Option[Double],
    repetitions: Option[Int],
    crossValidationFolds: Option[Int]
  ) = dispatch(_.regress(mlModelId, inputFieldNames, outputFieldName, filterId, featuresNormalizationType, pcaDims, trainingTestingSplit, repetitions, crossValidationFolds))

  override def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int]
  ) = dispatch(_.cluster(mlModelId, inputFieldNames, filterId, featuresNormalizationType, pcaDims))

  override def testIndependence(
    targetFieldName: String,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID]
  ) = dispatch(_.testIndependence(targetFieldName, inputFieldNames, filterId))

  override def getSeriesProcessingSpec = dispatch(_.getSeriesProcessingSpec)

  override def runSeriesProcessing = dispatch(_.runSeriesProcessing)

  override def getSeriesTransformationSpec = dispatch(_.getSeriesTransformationSpec)

  override def runSeriesTransformation = dispatch(_.runSeriesTransformation)

  override def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = dispatch(_.getFields(fieldTypeIds))

  override def getField(fieldName: String) = dispatch(_.getField(fieldName))

  override def getFieldNames = dispatch(_.getFieldNames)

  override def getFieldValue(
    id: BSONObjectID,
    fieldName: String
  ) = dispatch(_.getFieldValue(id, fieldName))

  override def exportRecordsAsCsv(
    dataViewId: BSONObjectID,
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsCsv(dataViewId, delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly))

  override def exportRecordsAsJson(
    dataViewId: BSONObjectID,
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsJson(dataViewId, filter, tableColumnsOnly))

  override def exportTranSMARTDataFile(
    delimiter : String
  ) = dispatch(_.exportTranSMARTDataFile(delimiter))

  override def exportTranSMARTMappingFile(
    delimiter : String
  ) = dispatch(_.exportTranSMARTMappingFile(delimiter))

  override def findCustom(
    filterOrId: Either[Seq[FilterCondition], BSONObjectID],
    orderBy: String,
    projection: Seq[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = dispatch(_.findCustom(filterOrId, orderBy, projection, limit, skip))

  override def getCategoriesWithFieldsAsTreeNodes(
    filterOrId: FilterOrId
  ) = dispatch(_.getCategoriesWithFieldsAsTreeNodes(filterOrId))
}