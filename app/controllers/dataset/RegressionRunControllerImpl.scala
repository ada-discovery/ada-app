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
import services.{DataSetService, DataSpaceService, WidgetGenerationService}
import services.ml._
import _root_.util.toHumanReadableCamel
import _root_.util.FieldUtil
import models.ml.regression.Regression.RegressionIdentity
import _root_.util.FieldUtil
import _root_.util.FieldUtil.caseClassToFlatFieldTypes
import _root_.util.toHumanReadableCamel
import models.json.OrdinalEnumFormat

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
    dataSetService: DataSetService,
    dataSpaceService: DataSpaceService,
    val wgs: WidgetGenerationService
  ) extends ReadonlyControllerImpl[RegressionResult, BSONObjectID]

    with RegressionRunController
    with WidgetRepoController[RegressionResult]
    with ExportableAction[RegressionResult]  {

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

  // export stuff
  private val exportOrderByFieldName = "timeCreated"
  private val csvFileName = "regression_results_" + dataSetId.replace(" ", "-") + ".csv"
  private val jsonFileName = "regression_results_" + dataSetId.replace(" ", "-") + ".json"

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  override protected val listViewColumns = Some(Seq(
    "setting-mlModelId",
    "setting-filterId",
    "setting-outputFieldName",
    "testStats-mae-mean",
    "testStats-mse-mean",
    "testStats-rmse-mean",
    "testStats-r2-mean",
    "timeCreated"
  ))

  // data set web context with all the routers
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

      (dataSetName + " Regression Run", dataSetName, page, widgets.flatten, fieldNameLabelMap, allRegressionRunFields, mlModelIdNameMap, filterIdNameMap, tree)
    }
  }

  override protected[controllers] def listView = { implicit ctx =>
    (view.list(_, _, _, _, _, _, _, _, _)).tupled
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

  private val fields = caseClassToFlatFieldTypes[RegressionResult]("-", Set("_id"))

  private case class RegressionResultExtra(dataSetId: String, mlModelName: Option[String], filterName: Option[String])
  private implicit val regressionResultExtraFormat = Json.format[RegressionResultExtra]

  private val extraFields = caseClassToFlatFieldTypes[RegressionResultExtra]()

  override def exportToDataSet(
    targetDataSetId: Option[String],
    targetDataSetName: Option[String]
  ) = Action.async { implicit request =>
    val newDataSetId = targetDataSetId.map(_.replace(' ', '_')).getOrElse(dataSetId + "_regression")

    for {
    // collect all the results
      allResults <- regressionResultsExtended

      // data set name
      dataSetName <- dsa.dataSetName

      // new data set name
      newDataSetName = targetDataSetName.getOrElse(dataSetName + " Regression")

      // register target dsa
      targetDsa <-
      dataSetService.register(
        dsa,
        newDataSetId,
        newDataSetName,
        StorageType.Mongo,
        "timeCreated"
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
          val regressionEvalMetricFieldSpec = fields.find(_._1.equals("setting-crossValidationEvalMetric")).get._2
          val vectorTransformTypeFieldSpec = fields.find(_._1.equals("setting-featuresNormalizationType")).get._2

          val regressionEvalMetricMap = regressionEvalMetricFieldSpec.enumValues.get.map { case (int, string) => (RegressionEvalMetric.withName(string), int)}
          val vectorTransformTypeMap = vectorTransformTypeFieldSpec.enumValues.get.map { case (int, string) => (VectorTransformType.withName(string), int)}

          val regressionEvalMetricFormat = OrdinalEnumFormat.enumFormat(regressionEvalMetricMap)
          val vectorTransformTypeFormat = OrdinalEnumFormat.enumFormat(vectorTransformTypeMap)

          implicit val regressionResultFormat = RegressionResult.createRegressionResultFormat(vectorTransformTypeFormat, regressionEvalMetricFormat)

          val resultJson = Json.toJson(result)(regressionResultFormat).as[JsObject]
          val extraResultJson = Json.toJson(extraResult).as[JsObject]
          resultJson ++ extraResultJson
        }
      )
    } yield
      Redirect(router.plainList).flashing("success" -> s"Regression results successfully exported to $newDataSetName.")
  }

  private def regressionResultsExtended: Future[Traversable[(RegressionResult, RegressionResultExtra)]] = {
    for {
    // get the results
      results <- repo.find()

      // add some extra stuff for easier reference (model and filter name)
      resultsWithExtra <- Future.sequence(
        results.map { result =>
          val regressionFuture = regressionRepo.get(result.setting.mlModelId)
          val filterFuture = result.setting.filterId.map(dsa.filterRepo.get).getOrElse(Future(None))

          for {
            mlModel <- regressionFuture
            filter <- filterFuture
          } yield
            (result, RegressionResultExtra(dsa.dataSetId, mlModel.flatMap(_.name), filter.flatMap(_.name)))
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
    println(tableColumnsOnly)
    val eolToUse = eol match {
      case Some(eol) => if (eol.trim.nonEmpty) eol.trim else csvEOL
      case None => csvEOL
    }
    val allFieldNames = fields.map(_._1).toSeq
    exportToCsv(
      csvFileName,
      delimiter,
      eolToUse,
      if (replaceEolWithSpace) csvCharReplacements else Nil)(
      Some(exportOrderByFieldName),
      filter,
      if (tableColumnsOnly) listViewColumns.get else Nil,
      if (tableColumnsOnly) listViewColumns else Some(allFieldNames)
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