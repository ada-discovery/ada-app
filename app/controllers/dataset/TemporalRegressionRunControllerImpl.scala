package controllers.dataset

import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import models.DataSetFormattersAndIds._
import models.json.OrdinalEnumFormat
import models.ml.regression.RegressionResult.temporalRegressionResultFormat
import models.{DistributionWidgetSpec, _}
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.spark_ml.MLResultUtil
import org.incal.spark_ml.models.VectorScalerType
import org.incal.spark_ml.models.regression.RegressionEvalMetric
import org.incal.spark_ml.models.result.TemporalRegressionResult
import org.incal.spark_ml.models.setting.{RegressionRunSpec, TemporalRegressionRunSpec}
import persistence.RepoTypes.RegressorRepo
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc.Action
import services.ml._
import services.{DataSetService, DataSpaceService, WidgetGenerationService}
import views.html.{regressionrun => view}

import scala.concurrent.Future

protected[controllers] class TemporalRegressionRunControllerImpl @Inject()(
  @Assisted dataSetId: String,
  dsaf: DataSetAccessorFactory,
  val mlMethodRepo: RegressorRepo,
  val mlService: MachineLearningService,
  val dataSetService: DataSetService,
  val dataSpaceService: DataSpaceService,
  val wgs: WidgetGenerationService
) extends RegressionRunControllerImpl[TemporalRegressionResult]
  with TemporalRegressionRunController {

  override protected def dsa = dsaf(dataSetId).get
  override protected val repo = dsa.temporalRegressionResultRepo

  override protected val router = new TemporalRegressionRunRouter(dataSetId)

  override protected val entityNameKey = "temporalRegressionRun"
  override protected val exportFileNamePrefix = "regression_results_"

  private val distributionDisplayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column), gridWidth = Some(3))

  override protected val widgetSpecs = Seq(
    DistributionWidgetSpec("testStats-mse-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-rmse-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-r2-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("testStats-mae-mean", None, displayOptions = distributionDisplayOptions),
    DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
    ScatterWidgetSpec("trainingStats-mse-mean", "testStats-mse-mean", Some("runSpec-mlModelId")),
    ScatterWidgetSpec("testStats-r2-mean", "testStats-mse-mean", Some("runSpec-mlModelId"))
  )

  override protected val listViewColumns = Some(Seq(
    "runSpec-mlModelId",
    "runSpec-ioSpec-filterId",
    "runSpec-ioSpec-outputFieldName",
    "testStats-mae-mean",
    "testStats-mse-mean",
    "testStats-rmse-mean",
    "testStats-r2-mean",
    "timeCreated"
  ))

  override protected def createView = { implicit ctx =>
    (view.create(_, _, _)).tupled
  }

  override def launch(
    runSpec: TemporalRegressionRunSpec,
    saveResults: Boolean
  ) = Action.async { implicit request =>
    val mlModelFuture = mlMethodRepo.get(runSpec.mlModelId)
    val criteriaFuture = loadCriteria(runSpec.ioSpec.filterId)
    val replicationCriteriaFuture = loadCriteria(runSpec.ioSpec.replicationFilterId)

    val fieldNames = runSpec.ioSpec.allFieldNames
    val fieldsFuture = dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

    def find(criteria: Seq[Criterion[Any]]) =
      dsa.dataSetRepo.find(criteria, projection = fieldNames)

    for {
      // load a ML model
      mlModel <- mlModelFuture

      // criteria
      criteria <- criteriaFuture

      // replication criteria
      replicationCriteria <- replicationCriteriaFuture

      // main data
      mainData <- find(criteria)

      // fields
      fields <- fieldsFuture

      // replication data
      replicationData <- if (replicationCriteria.nonEmpty) find(replicationCriteria) else Future(Nil)

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val fieldNameAndSpecs = fields.toSeq.map(field => (field.name, field.fieldTypeSpec))
        val ioSpec = runSpec.ioSpec
        val orderField = fields.find(_.name == ioSpec.orderFieldName).getOrElse(throw new AdaException(s"Order field ${ioSpec.outputFieldName} not found."))
        val orderFieldType = ftf(orderField.fieldTypeSpec).asValueOf[Any]
        val orderedValues = ioSpec.orderedStringValues.map(x => orderFieldType.displayStringToValue(x).get)

        val results = mlService.regressRowTemporalSeries(
          mainData, fieldNameAndSpecs, ioSpec.inputFieldNames, ioSpec.outputFieldName, ioSpec.orderFieldName, orderedValues, Some(ioSpec.groupIdFieldName),
          mlModel, runSpec.learningSetting, replicationData
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.map { resultsHolder =>
        // prepare the results stats
        val metricStatsMap = MLResultUtil.calcMetricStats(resultsHolder.performanceResults)

        if (saveResults) {
          val finalResult = MLResultUtil.createTemporalRegressionResult(runSpec, metricStatsMap)
          repo.save(finalResult)
        }

        val resultsJson = resultsToJson(RegressionEvalMetric)(metricStatsMap)

        logger.info("Regression finished with the following results:\n" + Json.prettyPrint(resultsJson))

        Ok(resultsJson)
      }.getOrElse(
        BadRequest(s"ML regression model with id ${runSpec.mlModelId.stringify} not found.")
      )
  }

  override protected def exportFormat=
    models.ml.regression.RegressionResult.createTemporalRegressionResultFormat(
      OrdinalEnumFormat(VectorScalerType),
      OrdinalEnumFormat(RegressionEvalMetric)
    )
}