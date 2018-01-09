package controllers.dataset

import javax.inject.Inject
import java.{util => ju}

import com.google.inject.assistedinject.Assisted
import controllers.DataSetWebContext
import dataaccess.{Criterion, FieldTypeHelper, RepoException}
import dataaccess.Criterion._
import models.{DistributionWidgetSpec, _}
import models.FilterCondition.{FilterIdentity, FilterOrId, toCriterion}
import models.DataSetFormattersAndIds._
import dataaccess.FilterRepoExtra._
import controllers.core._
import models.ml.{ClassificationSetting, _}
import models.ml.ClassificationResult.{classificationResultFormat, classificationSettingFormat}
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
import services.{DataSetService, DataSpaceService, StatsService, WidgetGenerationService}
import services.ml._
import _root_.util.toHumanReadableCamel
import _root_.util.FieldUtil
import models.ml.classification.Classification.ClassificationIdentity
import org.apache.commons.math3.stat.descriptive.SummaryStatistics

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
  ) extends ReadonlyControllerImpl[ClassificationResult, BSONObjectID]
    with ClassificationRunController
    with WidgetRepoController[ClassificationResult] {

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

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  protected val router = new ClassificationRunRouter(dataSetId)

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

  override protected[controllers] def showView = { implicit ctx =>
    (view.show(_, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    Page[ClassificationResult],
    Traversable[Widget],
    Map[String, String],
    Traversable[Field],
    Map[BSONObjectID, String],
    Map[BSONObjectID, String],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[ClassificationResult]
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

    val widgetsFuture = toCriteria(page.filterConditions).flatMap( criteria =>
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

      (dataSetName + " Classification Run", page, widgets.flatten, fieldNameLabelMap, allClassificationRunFields, mlModelIdNameMap, filterIdNameMap, tree)
    }
  }

  override protected[controllers] def listView = { implicit ctx =>
    (view.list(_, _, _, _, _, _, _, _)).tupled
  }

  // run

  override def create = Action.async { implicit request =>
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
        val results = mlService.classify(mainData, fieldNameAndSpecs, setting.outputFieldName, mlModel, setting.learningSetting, replicationData, setting.binCurvesNumBins)
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
    evalMetricStatsMap: Map[ClassificationEvalMetric.Value, (MetricStatsValues, MetricStatsValues, Option[MetricStatsValues])]
  ): JsArray = {
    val metricJsons = ClassificationEvalMetric.values.toSeq.sorted.flatMap { metric =>
      evalMetricStatsMap.get(metric).map { case (trainingStats, testStats, replicationStats) =>
        Json.obj(
          "metricName" -> toHumanReadableCamel(metric.toString),
          "trainEvalRate" -> trainingStats.mean,
          "testEvalRate" -> testStats.mean,
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

  override def selectFeaturesAsChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int,
    discretizerBucketsNum: Int
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
      val fieldNames = statsService.selectFeaturesAsChiSquare(jsons, fields, outputFieldName, featuresToSelectNum, discretizerBucketsNum)
      val json = JsArray(fieldNames.map(JsString(_)).toSeq)
      Ok(json)
    }
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
        case Some(filter) => toDataSetCriteria(filter.conditions)
        case None => Future(Nil)
      }
    } yield
      criteria

  private def toDataSetCriteria(
    conditions: Seq[FilterCondition]
  ): Future[Seq[Criterion[Any]]] =
    for {
      valueConverters <- {
        val fieldNames = conditions.map(_.fieldName)
        FieldUtil.valueConverters(dsa.fieldRepo, fieldNames)
      }
    } yield
      conditions.map(toCriterion(valueConverters)).flatten

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
      case i: RepoException =>
        Logger.error(s"Problem deleting the item ${id}")
        InternalServerError(i.getMessage)
    }
  }
}