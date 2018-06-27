package controllers.dataset

import javax.inject.Inject

import controllers.SecureControllerDispatcher
import models.FilterCondition.FilterOrId
import models.ml.VectorTransformType
import models.{FieldTypeId, FilterCondition, PageOrder}
import reactivemongo.bson.BSONObjectID
import models.ml.RegressionEvalMetric
import play.api.mvc.{Action, AnyContent}
import util.SecurityUtil._

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
  ) = dispatchAjax(_.getTable(page, orderBy, fieldNames, filterOrId))

  override def getWidgets(
    callbackId: String
  ) = dispatchAjax(_.getWidgets(callbackId))

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

  override def getDistributionWidget(
    fieldName: String,
    groupFieldName: Option[String],
    filterId: Option[BSONObjectID]
  ) = dispatch(_.getDistributionWidget(fieldName, groupFieldName, filterId))

  override def getCorrelations(
    filterOrId: FilterOrId
  ) = dispatch(_.getCorrelations(filterOrId))

  override def calcCorrelations(
    filterOrId: FilterOrId
  ) = dispatch(_.calcCorrelations(filterOrId))

  override def getIndependenceTest(
    filterOrId: FilterOrId
  ) = dispatch(_.getIndependenceTest(filterOrId))

  override def testIndependence(
    filterOrId: FilterOrId
  ) = dispatch(_.testIndependence(filterOrId))

  override def getCumulativeCount(
    dateFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId
  ) = dispatch(_.getCumulativeCount(dateFieldName, groupFieldName, filterOrId))

  override def getFractalis(
    fieldNameOption: Option[String]
  ) = dispatch(_.getFractalis(fieldNameOption))

  override def getClusterization = dispatch(_.getClusterization)

  override def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int]
  ) = dispatch(_.cluster(mlModelId, inputFieldNames, filterId, featuresNormalizationType, pcaDims))

  override def getSeriesProcessingSpec = dispatch(_.getSeriesProcessingSpec)

  override def runSeriesProcessing = dispatch(_.runSeriesProcessing)

  override def getSeriesTransformationSpec = dispatch(_.getSeriesTransformationSpec)

  override def runSeriesTransformation = dispatch(_.runSeriesTransformation)

  // field stuff

  override def getField(fieldName: String) = dispatchAjax(_.getField(fieldName))

  override def getFieldNamesAndLabels(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = dispatchAjax(_.getFieldNamesAndLabels(fieldTypeIds))

  override def getFieldTypeWithAllowedValues(
    fieldName: String
  ) = dispatchAjax(_.getFieldTypeWithAllowedValues(fieldName))

  override def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = dispatchAjax(_.getFields(fieldTypeIds))

  override def getFieldValue(
    id: BSONObjectID,
    fieldName: String
  ) = dispatchAjax(_.getFieldValue(id, fieldName))

  override def getCategoriesWithFieldsAsTreeNodes(
    filterOrId: FilterOrId
  ) = dispatchAjax(_.getCategoriesWithFieldsAsTreeNodes(filterOrId))

  // export

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

  // api function

  override def findCustom(
    filterOrId: Either[Seq[FilterCondition], BSONObjectID],
    orderBy: String,
    projection: Seq[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = dispatch(_.findCustom(filterOrId, orderBy, projection, limit, skip))
}