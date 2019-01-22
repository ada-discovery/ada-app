package controllers.dataset

import javax.inject.Inject
import java.{util => ju}

import com.google.inject.assistedinject.Assisted
import dataaccess.AdaDataAccessException
import models.{DistributionWidgetSpec, _}
import models.Filter.{FilterIdentity, FilterOrId}
import models.DataSetFormattersAndIds._
import dataaccess.FilterRepoExtra._
import models.ml.classification.ClassificationResult.classificationResultFormat
import models.Widget.{WidgetWrites, scatterWidgetFormat}
import persistence.RepoTypes.ClassificationRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc.{Action, Request}
import reactivemongo.bson.BSONObjectID
import services.{DataSetService, DataSpaceService, WidgetGenerationService}
import services.ml._
import _root_.util.FieldUtil
import _root_.util.FieldUtil.caseClassToFlatFieldTypes
import _root_.util.toHumanReadableCamel
import controllers.core.AdaReadonlyControllerImpl
import controllers.core.{ExportableAction, WidgetRepoController}
import field.FieldTypeHelper
import models.json.{EnumFormat, OrdinalEnumFormat}
import models.ml.classification.Classification.ClassificationIdentity
import services.stats.StatsService
import org.incal.core.FilterCondition
import org.incal.spark_ml.models.VectorScalerType
import org.incal.core.dataaccess.Criterion
import org.incal.play.Page
import org.incal.core.dataaccess.Criterion._
import org.incal.core.FilterCondition.toCriterion
import org.incal.play.controllers._
import org.incal.play.security.AuthAction
import org.incal.spark_ml.MachineLearningUtil
import org.incal.spark_ml.models.classification.ClassificationEvalMetric
import org.incal.spark_ml.models.results.{BinaryClassificationCurves, ClassificationResult, ClassificationSetting, MetricStatsValues}

import scala.reflect.runtime.universe.TypeTag
import views.html.{classificationrun => view}

import scala.concurrent.{Future, TimeoutException}

trait ClassificationRunControllerFactory {
  def apply(dataSetId: String): ClassificationRunController
}

protected[controllers] class ClassificationRunControllerImpl @Inject()(
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    classificationRepo: ClassificationRepo,
    mlService: MachineLearningService,
    statsService: StatsService,
    dataSetService: DataSetService,
    dataSpaceService: DataSpaceService,
    val wgs: WidgetGenerationService

  ) extends AdaReadonlyControllerImpl[ClassificationResult, BSONObjectID]
    with ClassificationRunController
    with WidgetRepoController[ClassificationResult]
    with ExportableAction[ClassificationResult]  {

  override protected val entityNameKey = "classificationRun"

  protected val dsa = dsaf(dataSetId).get
  override protected val repo = dsa.classificationResultRepo
  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private val doubleFieldType = ftf.apply(FieldTypeSpec(FieldTypeId.Double)).asValueOf[Any]
  private implicit val doubleScatterWidgetWrites = new WidgetWrites[Any](Seq(doubleFieldType, doubleFieldType))

  private val logger = Logger // (this.getClass())

  override protected val typeTag = implicitly[TypeTag[ClassificationResult]]
  override protected val format = classificationResultFormat

  private val distributionDisplayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column), gridWidth = Some(3))

  private val widgetSpecs = Seq(
    DistributionWidgetSpec("testStats-accuracy-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-weightedPrecision-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-weightedRecall-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-f1-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-areaUnderROC-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-areaUnderPR-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    ScatterWidgetSpec("trainingStats-accuracy-mean", "testStats-accuracy-mean", Some("setting-mlModelId")),
    ScatterWidgetSpec("testStats-areaUnderROC-mean", "testStats-accuracy-mean", Some("setting-mlModelId"))
  )

  // export stuff
  private val exportOrderByFieldName = "timeCreated"
  private val csvFileName = "classification_results_" + dataSetId.replace(" ", "-") + ".csv"
  private val jsonFileName = "classification_results_" + dataSetId.replace(" ", "-") + ".json"

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  override protected val listViewColumns = Some(Seq(
    "setting-mlModelId",
    "setting-filterId",
    "setting-outputFieldName",
    "testStats-accuracy-mean",
    "testStats-weightedPrecision-mean",
    "testStats-weightedRecall-mean",
    "testStats-f1-mean",
    "testStats-areaUnderROC-mean",
    "testStats-areaUnderPR-mean",
    "timeCreated"
  ))

  // data set web context with all the routers
  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  protected val router = new ClassificationRunRouter(dataSetId)

  override protected val homeCall = router.plainList

  // show view and data

  override protected type ShowViewData = (
    String,
    ClassificationResult,
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getShowViewData(
    id: BSONObjectID,
    item: ClassificationResult
  ) = { request =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val dataSetNameFuture = dsa.dataSetName

    for {
      dataSetName <- dataSetNameFuture
      tree <- treeFuture
    } yield
      (dataSetName + " Classification Run", item, tree)
  }

  override protected def showView = { implicit ctx =>
    (view.show(_, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    String,
    Page[ClassificationResult],
    Seq[FilterCondition],
    Traversable[Widget],
    Map[String, String],
    Traversable[Field],
    Map[BSONObjectID, String],
    Map[BSONObjectID, String],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[ClassificationResult],
    conditions: Seq[FilterCondition]
  ) = { request =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val nameFuture = dsa.dataSetName

    val fieldNames = page.items.flatMap { classificationResult =>
      val setting = classificationResult.setting
      setting.inputFieldNames ++ Seq(setting.outputFieldName)
    }.toSet

    val fieldsFuture = getDataSetFields(fieldNames)

    val allClassificationRunFieldsFuture = fieldCaseClassRepo.find()

    val mlModelIds = page.items.map(_.setting.mlModelId).toSet

    val mlModelsFuture = classificationRepo.find(Seq(ClassificationIdentity.name #-> mlModelIds.toSeq))

    val filterIds = page.items.map(_.setting.filterId).toSet

    val filtersFuture = dsa.filterRepo.find(Seq(FilterIdentity.name #-> filterIds.toSeq))

    val widgetsFuture = toCriteria(conditions).flatMap( criteria =>
      widgets(widgetSpecs, criteria)
    )

    for {
      tree <- treeFuture

      dataSetName <- nameFuture

      fields <- fieldsFuture

      allClassificationRunFields <- allClassificationRunFieldsFuture

      mlModels <- mlModelsFuture

      filters <- filtersFuture

      widgets <- widgetsFuture
    } yield {
      val fieldNameLabelMap = fields.map(field => (field.name, field.labelOrElseName)).toMap
      val mlModelIdNameMap = mlModels.map(mlModel => (mlModel._id.get, mlModel.name.get)).toMap
      val filterIdNameMap = filters.map(filter => (filter._id.get, filter.name.get)).toMap

      (dataSetName + " Classification Run", dataSetName, page, conditions, widgets.flatten, fieldNameLabelMap, allClassificationRunFields, mlModelIdNameMap, filterIdNameMap, tree)
    }
  }

  override protected def listView = { implicit ctx =>
    (view.list(_, _, _, _, _, _, _, _, _, _)).tupled
  }

  // run

  override def create = AuthAction { implicit request =>
    {
      for {
      // get the data set name, data space tree and the data set setting
       (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
      } yield {
        render {
          case Accepts.Html() => Ok(view.create(
            dataSetName,
            setting.filterShowFieldStyle,
            tree
          ))
          case Accepts.Json() => BadRequest("getClassification function doesn't support JSON response.")
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the create classification process")
        InternalServerError(t.getMessage)
    }
  }

  override def classify(
    setting: ClassificationSetting,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ) = Action.async { implicit request =>
    val mlModelFuture = classificationRepo.get(setting.mlModelId)
    val criteriaFuture = loadCriteria(setting.filterId)
    val replicationCriteriaFuture = loadCriteria(setting.replicationFilterId)

    val fieldNames = setting.fieldNamesToLoads
    val fieldsFuture =
      if (fieldNames.nonEmpty)
        dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))
      else
        dsa.fieldRepo.find()

    def findData(criteria: Seq[Criterion[Any]]) =
      if (fieldNames.nonEmpty)
        dsa.dataSetRepo.find(criteria, projection = fieldNames)
      else
        dsa.dataSetRepo.find(criteria)

    for {
      // load a ML model
      mlModel <- mlModelFuture

      // criteria
      criteria <- criteriaFuture

      // replication criteria
      replicationCriteria <- replicationCriteriaFuture

      // main data
      mainData <- findData(criteria)

      // fields
      fields <- fieldsFuture

      // replication data
      replicationData <- if (replicationCriteria.nonEmpty) findData(replicationCriteria) else Future(Nil)

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val selectedFields = setting.featuresSelectionNum.map { featuresSelectionNum =>
          val inputFields = fields.filter(!_.name.equals(setting.outputFieldName))
          val outputField = fields.find(_.name.equals(setting.outputFieldName)).get
          val selectedInputFields = statsService.selectFeaturesAsAnovaChiSquare(mainData, inputFields.toSeq, outputField, featuresSelectionNum)
          selectedInputFields ++ Seq(outputField)
        }.getOrElse(
          fields
        )

        val fieldNameAndSpecs = selectedFields.toSeq.map(field => (field.name, field.fieldTypeSpec))
        val results = mlService.classifyStatic(mainData, fieldNameAndSpecs, setting.outputFieldName, mlModel, setting.learningSetting, replicationData, setting.binCurvesNumBins)
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.map { resultsHolder =>
        // prepare the results stats
        val metricStatsMap = MachineLearningUtil.calcMetricStats(resultsHolder.performanceResults)

        if (saveResults) {
          val binCurves = if (saveBinCurves) resultsHolder.binCurves else Nil
          val finalResult = MachineLearningUtil.createClassificationResult(setting, metricStatsMap, binCurves)
          repo.save(finalResult)
        }

        val resultsJson = resultsToJson(metricStatsMap)
        val replicationCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._3), 350)
        val height = if (replicationCurves.nonEmpty) 350 else 500
        val trainingCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._1), height)
        val testCurves = binCurvesToWidgets(resultsHolder.binCurves.flatMap(_._2), height)

        logger.info("Classification finished with the following results:\n" + Json.prettyPrint(resultsJson))

        val json = Json.obj(
          "results" -> resultsJson,
          "trainingCurves" -> Json.toJson(trainingCurves.toSeq),
          "testCurves" -> Json.toJson(testCurves.toSeq),
          "replicationCurves" -> Json.toJson(replicationCurves.toSeq)
        )
        Ok(json)
      }.getOrElse(
        BadRequest(s"ML classification model with id ${setting.mlModelId.stringify} not found.")
      )
  }

  private def resultsToJson(
    evalMetricStatsMap: Map[ClassificationEvalMetric.Value, (MetricStatsValues, Option[MetricStatsValues], Option[MetricStatsValues])]
  ): JsArray = {
    val metricJsons = ClassificationEvalMetric.values.toSeq.sorted.flatMap { metric =>
      evalMetricStatsMap.get(metric).map { case (trainingStats, testStats, replicationStats) =>
        Json.obj(
          "metricName" -> toHumanReadableCamel(metric.toString),
          "trainEvalRate" -> trainingStats.mean,
          "testEvalRate" -> testStats.map(_.mean),
          "replicationEvalRate" -> replicationStats.map(_.mean)
        )
      }
    }

    JsArray(metricJsons)
  }

  private def binCurvesToWidgets(
    binCurves: Traversable[BinaryClassificationCurves],
    height: Int
  ): Traversable[Widget] = {
    def widget(title: String, xCaption: String, yCaption: String, series: Traversable[Seq[(Double, Double)]]) = {
      if (series.exists(_.nonEmpty)) {
        val data = series.toSeq.zipWithIndex.map { case (data, index) =>
          ("Run " + (index + 1).toString, data)
        }
        val displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(height))
        val widget = LineWidget[Double](
          title, xCaption, yCaption, data, Some(0), Some(1), Some(0), Some(1), displayOptions)
        Some(widget)
        //      ScatterWidget(title, xCaption, yCaption, data, BasicDisplayOptions(gridWidth = Some(6), height = Some(450)))
      } else
        None
    }

    val rocWidget = widget("ROC", "FPR", "TPR", binCurves.map(_.roc))
    val prWidget = widget("PR", "Recall", "Precision", binCurves.map(_.precisionRecall))
    val fMeasureThresholdWidget = widget("FMeasure by Threshold", "Threshold", "F-Measure", binCurves.map(_.fMeasureThreshold))
    val precisionThresholdWidget = widget("Precision by Threshold", "Threshold", "Precision", binCurves.map(_.precisionThreshold))
    val recallThresholdWidget = widget("Recall by Threshold", "Threshold", "Recall", binCurves.map(_.recallThreshold))

    Seq(
      rocWidget, prWidget, fMeasureThresholdWidget, precisionThresholdWidget, recallThresholdWidget
    ).flatten
  }

  override def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
  ) = Action.async { implicit request =>
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq
      else
        Nil

    val criteriaFuture = loadCriteria(filterId)

    for {
      criteria <- criteriaFuture
      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, criteria)
    } yield {
      val inputFields = fields.filter(!_.name.equals(outputFieldName))
      val outputField = fields.find(_.name.equals(outputFieldName)).get
      val selectedFields = statsService.selectFeaturesAsAnovaChiSquare(jsons, inputFields, outputField, featuresToSelectNum)
      val json = JsArray(selectedFields.map(field => JsString(field.name)))
      Ok(json)
    }
  }

  private def loadCriteria(filterId: Option[BSONObjectID]) =
    for {
      filter <- filterId match {
        case Some(filterId) => dsa.filterRepo.get(filterId)
        case None => Future(None)
      }

      criteria <- filter match {
        case Some(filter) => FieldUtil.toDataSetCriteria(dsa.fieldRepo, filter.conditions)
        case None => Future(Nil)
      }
    } yield
      criteria

  private def getDataSetFields(fieldNames: Traversable[String]) =
    if (fieldNames.nonEmpty)
      dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSeq))
    else
      Future(Nil)

  private def getDataSetNameTreeAndSetting(request: Request[_]): Future[(String, Traversable[DataSpaceMetaInfo], DataSetSetting)] = {
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val settingFuture = dsa.setting

    for {
    // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space tree
      dataSpaceTree <- treeFuture

      // get the data set setting
      setting <- settingFuture
    } yield
      (dataSetName, dataSpaceTree, setting)
  }

  override protected def filterValueConverters(
    fieldNames: Traversable[String]
  ) = FieldUtil.valueConverters(fieldCaseClassRepo, fieldNames)

  override def delete(id: BSONObjectID) = Action.async { implicit request =>
    repo.delete(id).map { _ =>
      render {
        case Accepts.Html() => Redirect(router.plainList).flashing("success" -> s"Item ${id.stringify} has been deleted")
        case Accepts.Json() => Ok(Json.obj("message" -> "Item successfully deleted", "id" -> id.toString))
      }
    }.recover {
      case e: AdaException =>
        Logger.error(s"Problem deleting the item ${id}")
        BadRequest(e.getMessage)
      case t: TimeoutException =>
        Logger.error(s"Problem deleting the item ${id}")
        InternalServerError(t.getMessage)
      case i: AdaDataAccessException =>
        Logger.error(s"Problem deleting the item ${id}")
        InternalServerError(i.getMessage)
    }
  }

  private val fields = caseClassToFlatFieldTypes[ClassificationResult]("-", Set("_id"))
  private val allFieldNames = fields.map(_._1).toSeq

  private case class ClassificationResultExtra(dataSetId: String, mlModelName: Option[String], filterName: Option[String])
  private implicit val classificationResultExtraFormat = Json.format[ClassificationResultExtra]

  private val extraFields = caseClassToFlatFieldTypes[ClassificationResultExtra]()

  override def exportToDataSet(
    targetDataSetId: Option[String],
    targetDataSetName: Option[String]
  ) = Action.async { implicit request =>
    val newDataSetId = targetDataSetId.map(_.replace(' ', '_')).getOrElse(dataSetId + "_classification")

    for {
      // collect all the results
      allResults <- classificationResultsExtended

      // data set name
      dataSetName <- dsa.dataSetName

      // new data set name
      newDataSetName = targetDataSetName.getOrElse(dataSetName + " Classification")

      // register target dsa
      targetDsa <-
        dataSetService.register(
          dsa,
          newDataSetId,
          newDataSetName,
          StorageType.Mongo
        )

      // update the dictionary
      _ <- {
        val newFields = (fields ++ extraFields).map { case (name, fieldTypeSpec) =>
          val roundedFieldSpec =
            if (fieldTypeSpec.fieldType == FieldTypeId.Double)
              fieldTypeSpec.copy(displayDecimalPlaces = Some(3))
            else
              fieldTypeSpec

          val stringEnums = roundedFieldSpec.enumValues.map(_.map { case (from, to) => (from.toString, to)})
          val label = toHumanReadableCamel(name.replaceAllLiterally("-", " ").replaceAllLiterally("Stats", ""))
          Field(name, Some(label), roundedFieldSpec.fieldType, roundedFieldSpec.isArray, stringEnums, roundedFieldSpec.displayDecimalPlaces)
        }
        dataSetService.updateDictionaryFields(newDataSetId, newFields, false, true)
      }

      // delete the old results (if any)
      _ <- targetDsa.dataSetRepo.deleteAll

      // save the results
      _ <- targetDsa.dataSetRepo.save(
        allResults.map { case (result, extraResult) =>
          val classificationEvalMetricFieldSpec = fields.find(_._1.equals("setting-crossValidationEvalMetric")).get._2
          val vectorTransformTypeFieldSpec = fields.find(_._1.equals("setting-featuresNormalizationType")).get._2
          val subsamplingRatiosFieldSpec = fields.find(_._1.equals("setting-samplingRatios")).get._2

          val classificationEvalMetricMap = classificationEvalMetricFieldSpec.enumValues.get.map { case (int, string) => (ClassificationEvalMetric.withName(string), int)}
          val vectorTransformTypeMap = vectorTransformTypeFieldSpec.enumValues.get.map { case (int, string) => (VectorScalerType.withName(string), int)}

          val classificationEvalMetricFormat = OrdinalEnumFormat.enumFormat(classificationEvalMetricMap)
          val vectorTransformTypeFormat = OrdinalEnumFormat.enumFormat(vectorTransformTypeMap)

          implicit val classificationResultFormat = models.ml.classification.ClassificationResult.createClassificationResultFormat(vectorTransformTypeFormat, classificationEvalMetricFormat)

          val resultJson = Json.toJson(result)(classificationResultFormat).as[JsObject]

          // handle sampling ratios, which are stored as an unstructured array
          val newSamplingRatioJson = (resultJson \ "setting-samplingRatios").asOpt[JsArray].map { jsonArray =>
            val samplingRatioJsons = jsonArray.value.map { case json: JsArray =>
              val outputValue = json.value(0)
              val samplingRatio = json.value(1)
              Json.obj("outputValue" -> outputValue, "samplingRatio" -> samplingRatio)
            }
            JsArray(samplingRatioJsons)
          }.getOrElse(JsNull)

          val extraResultJson = Json.toJson(extraResult).as[JsObject]
          resultJson.+("setting-samplingRatios", newSamplingRatioJson) ++ extraResultJson
        }
      )
    } yield
      Redirect(router.plainList).flashing("success" -> s"Classification results successfully exported to $newDataSetName.")
  }

  private def classificationResultsExtended: Future[Traversable[(ClassificationResult, ClassificationResultExtra)]] = {
    for {
      // get the results
      results <- repo.find()

      // add some extra stuff for easier reference (model and filter name)
      resultsWithExtra <- Future.sequence(
        results.map { result =>
          val classificationFuture = classificationRepo.get(result.setting.mlModelId)
          val filterFuture = result.setting.filterId.map(dsa.filterRepo.get).getOrElse(Future(None))

          for {
            mlModel <- classificationFuture
            filter <- filterFuture
          } yield
            (result, ClassificationResultExtra(dsa.dataSetId, mlModel.flatMap(_.name), filter.flatMap(_.name)))
        }
      )
    } yield
      resultsWithExtra
  }

  // exporting

  override def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = {
    val eolToUse = eol match {
      case Some(eol) => if (eol.trim.nonEmpty) eol.trim else csvEOL
      case None => csvEOL
    }

    val fieldsNames = if (tableColumnsOnly) listViewColumns.get else allFieldNames

    exportToCsv(
      csvFileName,
      delimiter,
      eolToUse,
      if (replaceEolWithSpace) csvCharReplacements else Nil)(
      fieldsNames,
      Some(exportOrderByFieldName),
      filter
    )
  }

  override def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) =
    exportToJson(
      jsonFileName)(
      Some(exportOrderByFieldName),
      filter,
      if (tableColumnsOnly) listViewColumns.get else Nil
    )
}