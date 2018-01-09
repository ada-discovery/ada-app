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
import models.ml.{RegressionSetting, _}
import models.ml.RegressionResult.{regressionResultFormat, regressionSettingFormat}
import models.Widget.{WidgetWrites, scatterWidgetFormat}
import persistence.RepoTypes.RegressionRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc.{Action, Request}
import reactivemongo.bson.BSONObjectID
import services.{DataSpaceService, WidgetGenerationService}
import services.ml._
import _root_.util.toHumanReadableCamel
import _root_.util.FieldUtil
import models.ml.regression.Regression.RegressionIdentity

import scala.reflect.runtime.universe.TypeTag
import views.html.{regressionrun => view}

import scala.concurrent.{Future, TimeoutException}

trait RegressionRunControllerFactory {
  def apply(dataSetId: String): RegressionRunController
}

protected[controllers] class RegressionRunControllerImpl @Inject()(
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    regressionRepo: RegressionRepo,
    mlService: MachineLearningService,
    dataSpaceService: DataSpaceService,
    val wgs: WidgetGenerationService
  ) extends ReadonlyControllerImpl[RegressionResult, BSONObjectID]
    with RegressionRunController
    with WidgetRepoController[RegressionResult] {

  override protected val entityNameKey = "regressionRun"

  protected val dsa = dsaf(dataSetId).get
  override protected val repo = dsa.regressionResultRepo
  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private val doubleFieldType = ftf.apply(FieldTypeSpec(FieldTypeId.Double)).asValueOf[Any]
  private implicit val doubleScatterWidgetWrites = new WidgetWrites[Any](Seq(doubleFieldType, doubleFieldType))

  private val logger = Logger // (this.getClass())

  override protected val typeTag = implicitly[TypeTag[RegressionResult]]
  override protected val format = regressionResultFormat

  private val distributionDisplayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column), gridWidth = Some(3))

  private val widgetSpecs = Seq(
    DistributionWidgetSpec("testStats-mse-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-rmse-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-r2-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-mae-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    ScatterWidgetSpec("trainingStats-mse-mean", "testStats-mse-mean", Some("setting-mlModelId")),
    ScatterWidgetSpec("testStats-r2-mean", "testStats-mse-mean", Some("setting-mlModelId"))
  )

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  protected val router = new RegressionRunRouter(dataSetId)

  // show view and data

  override protected type ShowViewData = (
    String,
    RegressionResult,
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getShowViewData(
    id: BSONObjectID,
    item: RegressionResult
  ) = { request =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val dataSetNameFuture = dsa.dataSetName

    for {
      dataSetName <- dataSetNameFuture
      tree <- treeFuture
    } yield
      (dataSetName + " Regression Run", item, tree)
  }

  override protected[controllers] def showView = { implicit ctx =>
    (view.show(_, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    Page[RegressionResult],
    Traversable[Widget],
    Map[String, String],
    Traversable[Field],
    Map[BSONObjectID, String],
    Map[BSONObjectID, String],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[RegressionResult]
  ) = { request =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val nameFuture = dsa.dataSetName

    val fieldNames = page.items.flatMap { regressionResult =>
      val setting = regressionResult.setting
      setting.inputFieldNames ++ Seq(setting.outputFieldName)
    }.toSet

    val fieldsFuture = getDataSetFields(fieldNames)

    val allRegressionRunFieldsFuture = fieldCaseClassRepo.find()

    val mlModelIds = page.items.map(_.setting.mlModelId).toSet

    val mlModelsFuture = regressionRepo.find(Seq(RegressionIdentity.name #-> mlModelIds.toSeq))

    val filterIds = page.items.map(_.setting.filterId).toSet

    val filtersFuture = dsa.filterRepo.find(Seq(FilterIdentity.name #-> filterIds.toSeq))

    val widgetsFuture = toCriteria(page.filterConditions).flatMap( criteria =>
      widgets(widgetSpecs, criteria)
    )

    for {
      tree <- treeFuture

      dataSetName <- nameFuture

      fields <- fieldsFuture

      allRegressionRunFields <- allRegressionRunFieldsFuture

      mlModels <- mlModelsFuture

      filters <- filtersFuture

      widgets <- widgetsFuture
    } yield {
      val fieldNameLabelMap = fields.map(field => (field.name, field.labelOrElseName)).toMap
      val mlModelIdNameMap = mlModels.map(mlModel => (mlModel._id.get, mlModel.name.get)).toMap
      val filterIdNameMap = filters.map(filter => (filter._id.get, filter.name.get)).toMap

      (dataSetName + " Regression Run", page, widgets.flatten, fieldNameLabelMap, allRegressionRunFields, mlModelIdNameMap, filterIdNameMap, tree)
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
          case Accepts.Json() => BadRequest("getRegression function doesn't support JSON response.")
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the create regression process")
        InternalServerError(t.getMessage)
    }
  }

  override def regress(
    setting: RegressionSetting,
    saveResults: Boolean
  ) = Action.async { implicit request =>
    println(Json.prettyPrint(Json.toJson(setting)))

    val mlModelFuture = regressionRepo.get(setting.mlModelId)
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
        val fieldNameAndSpecs = fields.toSeq.map(field => (field.name, field.fieldTypeSpec))
        val results = mlService.regress(mainData, fieldNameAndSpecs, setting.outputFieldName, mlModel, setting.learningSetting, replicationData)
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.map { resultsHolder =>
        // prepare the results stats
        val metricStatsMap = MachineLearningUtil.calcMetricStats(resultsHolder.performanceResults)

        if (saveResults) {
          val finalResult = MachineLearningUtil.createRegressionResult(setting, metricStatsMap)
          repo.save(finalResult)
        }

        val resultsJson = resultsToJson(metricStatsMap)

        logger.info("Regression finished with the following results:\n" + Json.prettyPrint(resultsJson))

        Ok(resultsJson)
      }.getOrElse(
        BadRequest(s"ML regression model with id ${setting.mlModelId.stringify} not found.")
      )
  }

  private def resultsToJson(
    evalMetricStatsMap: Map[RegressionEvalMetric.Value, (MetricStatsValues, MetricStatsValues, Option[MetricStatsValues])]
  ): JsArray = {
    val metricJsons = RegressionEvalMetric.values.toSeq.sorted.flatMap { metric =>
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

//  override def regress(
//                        mlModelId: BSONObjectID,
//                        inputFieldNames: Seq[String],
//                        outputFieldName: String,
//                        filterId: Option[BSONObjectID],
//                        featuresNormalizationType: Option[VectorTransformType.Value],
//                        pcaDims: Option[Int],
//                        trainingTestingSplit: Option[Double],
//                        repetitions: Option[Int],
//                        crossValidationFolds: Option[Int],
//                        crossValidationEvalMetric: Option[RegressionEvalMetric.Value]
//                      ) = Action.async { implicit request =>
//    val learningSetting = LearningSetting[RegressionEvalMetric.Value](featuresNormalizationType, pcaDims, trainingTestingSplit, None, Nil, repetitions, crossValidationFolds, crossValidationEvalMetric)
//
//    for {
//      result <- runMLAux(
//        regressionRepo.get(mlModelId),
//        mlService.regress)(
//        inputFieldNames, outputFieldName, filterId, learningSetting
//      )
//    } yield
//      result match {
//        case Some(result) =>
//          logger.info("Regression finished with the following results: " + Json.stringify(result))
//          Ok(result)
//        case None =>
//          BadRequest(s"ML regression model with id ${mlModelId.stringify} not found.")
//      }
//  }
//
//  private def runMLAux[M](
//                           getModel: => Future[Option[M]],
//                           runML: (Traversable[JsObject], Seq[(String, FieldTypeSpec)], String, M, LearningSetting[RegressionEvalMetric.Value]) => Future[Traversable[Performance]])(
//                           inputFieldNames: Seq[String],
//                           outputFieldName: String,
//                           filterId: Option[BSONObjectID],
//                           learningSetting: LearningSetting[RegressionEvalMetric.Value]
//                         ): Future[Option[JsArray]] = {
//    val explFieldNamesToLoads =
//      if (inputFieldNames.nonEmpty)
//        (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq
//      else
//        Nil
//
//    val criteriaFuture = loadCriteria(filterId)
//
//    for {
//      mlModel <- getModel
//
//      criteria <- criteriaFuture
//
//      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, criteria)
//
//      evalRates <- mlModel.map { mlModel =>
//        val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
//        runML(jsons, fieldNameAndSpecs, outputFieldName, mlModel, learningSetting)
//      }.getOrElse(
//        Future(Nil)
//      )
//    } yield
//      mlModel.map { mlModel =>
//        val evalJsons = evalRates.toSeq.sortBy(_.evalMetric.id).map { performance =>
//          val results = performance.trainingTestResults
//          Json.obj(
//            "metricName" -> toHumanReadableCamel(performance.evalMetric.toString),
//            "trainEvalRate" -> results.map(_._1).sum / results.size, // mean
//            "testEvalRate" -> results.map(_._2).sum / results.size   // mean
//          )
//        }
//
//        JsArray(evalJsons)
//      }
//  }
}