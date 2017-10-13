package controllers.dataset

import javax.inject.Inject
import java.{util => ju}

import controllers.core.GenericMapping
import com.google.inject.assistedinject.Assisted
import controllers.DataSetWebContext
import dataaccess.Criterion
import dataaccess.Criterion._
import models.{DistributionWidgetSpec, _}
import models.FilterCondition.{FilterIdentity, FilterOrId, toCriterion}
import models.DataSetFormattersAndIds._
import dataaccess.FilterRepoExtra._
import controllers.core._
import models.ml.{ClassificationSetting, _}
import models.ml.ClassificationResult.{classificationResultFormat, classificationSettingFormat}
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
    dataSetService: DataSetService,
    dataSpaceService: DataSpaceService,
    val wgs: WidgetGenerationService
  ) extends ReadonlyControllerImpl[ClassificationResult, BSONObjectID]
    with ClassificationRunController
    with WidgetRepoController[ClassificationResult] {

  protected val dsa = dsaf(dataSetId).get
  override protected val repo = dsa.classificationResultRepo

  private val logger = Logger // (this.getClass())

  override protected val typeTag = implicitly[TypeTag[ClassificationResult]]
  override protected val format = classificationResultFormat

  //  protected override val listViewColumns = Some(Seq(CategoryIdentity.name, "name", "label"))

  //  override protected[controllers] val form = Form(GenericMapping(typeOf[ClassificationSetting]))

//  private val settingAndResultsFields =
//    FieldUtil.caseClassToFlatFieldTypes[ClassificationResult]("-").filter(_._1 != "_id")

  private val widgetSpecs = Seq(
    DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    DistributionWidgetSpec("testStats-accuracy-mean", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    DistributionWidgetSpec("testStats-accuracy-mean", Some("setting-mlModelId")),
    ScatterWidgetSpec("trainingStats-accuracy-mean", "testStats-accuracy-mean", None)
  )

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

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
    saveResults: Boolean
  ) = Action.async { implicit request =>
    val mlModelFuture = classificationRepo.get(setting.mlModelId)
    val criteriaFuture = loadCriteria(setting.filterId)

    for {
      mlModel <- mlModelFuture

      criteria <- criteriaFuture

      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, setting.fieldNamesToLoads, criteria)

      results <- mlModel.map { mlModel =>
        val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
        mlService.classify(jsons, fieldNameAndSpecs, setting.outputFieldName, mlModel, setting.learningSetting)
      }.getOrElse(
        Future(Nil)
      )
    } yield
      mlModel.map { _ =>
        // prepare the results stats
        val metricStatsMap = MachineLearningUtil.calcClassificationMetricStats(results.toSeq)

        if (saveResults) {
          val finalResult = MachineLearningUtil.createClassificationResult(metricStatsMap, setting)
          repo.save(finalResult)
        }

        val json = resultsToJson(metricStatsMap)

        logger.info("Classification finished with the following results:\n" + Json.prettyPrint(json))
        Ok(json)
      }.getOrElse(
        BadRequest(s"ML classification model with id ${setting.mlModelId.stringify} not found.")
      )
  }

  private def resultsToJson(
    evalMetricStatsMap: Map[ClassificationEvalMetric.Value, (MetricStatsValues, MetricStatsValues)]
  ): JsArray = {
    val metricJsons = ClassificationEvalMetric.values.toSeq.sorted.flatMap { metric =>
      evalMetricStatsMap.get(metric).map { case (trainingStats, testStats) =>
        Json.obj(
          "metricName" -> toHumanReadableCamel(metric.toString),
          "trainEvalRate" -> trainingStats.mean,
          "testEvalRate" -> testStats.mean
        )
      }
    }

    JsArray(metricJsons)
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
      val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
      val fieldNames = mlService.selectFeaturesAsChiSquare(jsons, fieldNameAndSpecs, outputFieldName, featuresToSelectNum, discretizerBucketsNum)
      val json = JsArray(fieldNames.map(JsString(_)).toSeq)
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
}