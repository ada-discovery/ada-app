package controllers.dataset

import controllers.{GenericJsRouter, GenericRouter}

import scalaz.Scalaz._

/**
  * Container for various calls from Controllers.
  * To be passed to other modules like views to simplify data access.
  */
class DataSetRouter(dataSetId: String) extends GenericRouter(routes.DataSetDispatcher, "dataSet", dataSetId) {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val getView = routes.getView _ map route
  val getDefaultView = routeFun(_.getDefaultView)
  val get = routes.get _ map route
  val plainGetScatterStats = routeFun(_.getScatterStats())
  val getScatterStats = routes.getScatterStats _ map route
  val plainGetDistribution = routeFun(_.getDistribution())
  val getDistribution = routes.getDistribution _ map route
  val getCumulativeCount = routes.getCumulativeCount _ map route
  val getCorrelations = routes.getCorrelations _ map route
  val getClusterization = routeFun(_.getClusterization)
  val getIndependenceTest = routeFun(_.getIndependenceTest)
  val getSeriesProcessingSpec = routeFun(_.getSeriesProcessingSpec)
  val runSeriesProcessing = routeFun(_.runSeriesProcessing)
  val getSeriesTransformationSpec = routeFun(_.getSeriesTransformationSpec)
  val runSeriesTransformation = routeFun(_.runSeriesTransformation)
  val getFractalis = routes.getFractalis _ map route
  val getTable = routes.getTable _ map route
  val fields = routes.getFields _ map route
  val allFields = routeFun(_.getFields())
  val allFieldNamesAndLabels = routeFun(_.getFieldNamesAndLabels())
  val fieldNames = routeFun(_.getFieldNames)
  val getFieldValue = routes.getFieldValue _ map route
  val exportCsv = routes.exportRecordsAsCsv _ map route
  val exportJson  = routes.exportRecordsAsJson _ map route
  val exportTranSMARTData = routeFun(_.exportTranSMARTDataFile())
  val exportTranSMARTMapping = routeFun(_.exportTranSMARTMappingFile())
  val getCategoriesWithFieldsAsTreeNodes = routes.getCategoriesWithFieldsAsTreeNodes _ map route
  val findCustom = routes.findCustom _ map route
}

final class DataSetJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.DataSetDispatcher, "dataSet", dataSetId) {
  val getFieldValue = routeFun(_.getFieldValue)
  val getField = routeFun(_.getField)
  val getFieldTypeWithAllowedValues = routeFun(_.getFieldTypeWithAllowedValues)
  val getWidgets = routeFun(_.getWidgets)
  val getView = routeFun(_.getView)
  val cluster = routeFun(_.cluster)
  val getDistributionWidget = routeFun(_.getDistributionWidget)
  val testIndependence = routeFun(_.testIndependence)
  val calcCorrelations = routeFun(_.calcCorrelations)
}