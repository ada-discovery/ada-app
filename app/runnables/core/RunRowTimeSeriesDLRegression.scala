package runnables.core

import javax.inject.Inject

import field.FieldTypeHelper
import models.DataSetFormattersAndIds.FieldIdentity
import persistence.RepoTypes.RegressionRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.bson.BSONObjectID
import services.ml.MachineLearningService
import org.incal.core.InputFutureRunnable
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.spark_ml.models.VectorScalerType
import org.incal.spark_ml.models.LearningSetting
import org.incal.spark_ml.models.regression.RegressionEvalMetric

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunRowTimeSeriesDLRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressionRepo
  ) extends InputFutureRunnable[RunRowTimeSeriesDLRegressionSpec] with TimeSeriesResultsHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(
    input: RunRowTimeSeriesDLRegressionSpec
  ): Future[Unit] = {
    val dsa = dsaf(input.dataSetId).get

    val fieldNames = (input.inputFieldNames ++ Seq(input.outputFieldName, input.orderFieldName)).toSet.toSeq

    for {
      // load a ML model
      mlModel <- regressionRepo.get(input.mlModelId)

      // get all the fields
      fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))
      fieldNameSpecs = fields.map(field => (field.name, field.fieldTypeSpec)).toSeq

      // order field (and type)
      orderField <- dsa.fieldRepo.get(input.orderFieldName).map(_.get)
      orderFieldType = ftf(orderField.fieldTypeSpec).asValueOf[Any]
      orderedValues = input.orderedStringValues.map(x => orderFieldType.displayStringToValue(x).get)

      // criterion field (and type)
      criterionField <- dsa.fieldRepo.get(input.criterionFieldName).map(_.get)
      criterionFieldType = ftf(criterionField.fieldTypeSpec).asValueOf[Any]
      criterionValue = criterionFieldType.displayStringToValue(input.criterionStringValue).get

      // jsons
      data <- dsa.dataSetRepo.find(criteria = Seq(input.criterionFieldName #== criterionValue), projection = fieldNames)

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val results = mlService.regressRowTimeSeries(
          data,
          fieldNameSpecs,
          input.inputFieldNames,
          input.outputFieldName,
          input.orderFieldName,
          orderedValues,
          input.predictAhead,
          Some(input.windowSize),
          None,
          mlModel,
          input.learningSetting,
          input.crossValidationMinTrainingSize,
          Nil
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.foreach(exportResults)
  }

  override def inputType = typeOf[RunRowTimeSeriesDLRegressionSpec]
}

case class RunRowTimeSeriesDLRegressionSpec(
  dataSetId: String,
  inputFieldNames: Seq[String],
  outputFieldName: String,
  orderFieldName: String,
  orderedStringValues: Seq[String],
  criterionFieldName: String,
  criterionStringValue: String,
  mlModelId: BSONObjectID,
  predictAhead: Int,
  windowSize: Int,
  featuresNormalizationType: Option[VectorScalerType.Value],
  pcaDims: Option[Int],
  trainingTestingSplit: Option[Double],
  repetitions: Option[Int],
  crossValidationFolds: Option[Int],
  crossValidationMinTrainingSize: Option[Double],
  crossValidationEvalMetric: Option[RegressionEvalMetric.Value]
) {
  def learningSetting =
    LearningSetting[RegressionEvalMetric.Value](featuresNormalizationType, pcaDims, trainingTestingSplit, Nil, repetitions, crossValidationFolds, crossValidationEvalMetric)
}