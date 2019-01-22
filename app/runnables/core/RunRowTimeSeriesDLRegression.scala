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
import org.incal.core.dataaccess.NotEqualsNullCriterion
import org.incal.spark_ml.models.VectorScalerType
import org.incal.spark_ml.models.LearningSetting
import org.incal.spark_ml.models.regression.RegressionEvalMetric
import util.FieldUtil

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

    val fieldNames = (input.inputFieldNames ++ Seq(input.outputFieldName, input.orderFieldName, input.idFieldName)).toSet.toSeq

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

      // id field (and type)
      idField <- dsa.fieldRepo.get(input.idFieldName).map(_.get)
      idFieldType = ftf(idField.fieldTypeSpec).asValueOf[Any]

      // id match criterion (if requested)
      idMatchCriterion = input.idStringValue.map(idStringValue =>
        input.idFieldName #== idFieldType.displayStringToValue(idStringValue).get
      )

      // filter criteria
      filterCriteria <- loadCriteria(dsa, input.filterId)

      // not null field criteria
      notNullFieldCriteria = fields.map(field => NotEqualsNullCriterion(field.name))

      // jsons
      data <- dsa.dataSetRepo.find(
        criteria = filterCriteria ++ notNullFieldCriteria ++ Seq(idMatchCriterion).flatten,
        projection = fieldNames
      )

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val results = mlService.regressRowTemporalSeries(
          data,
          fieldNameSpecs,
          input.inputFieldNames,
          input.outputFieldName,
          input.orderFieldName,
          orderedValues,
          Some(input.idFieldName),
          input.predictAhead,
          Some(input.windowSize),
          None,
          mlModel,
          input.learningSetting,
          input.outputNormalizationType,
          input.crossValidationMinTrainingSizeRatio,
          input.trainingTestSplitOrderValue,
          Nil
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.foreach(exportResults)
  }

  private def loadCriteria(dsa: DataSetAccessor, filterId: Option[BSONObjectID]) =
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

  override def inputType = typeOf[RunRowTimeSeriesDLRegressionSpec]
}

case class RunRowTimeSeriesDLRegressionSpec(
  dataSetId: String,
  inputFieldNames: Seq[String],
  outputFieldName: String,
  orderFieldName: String,
  orderedStringValues: Seq[String],
  idFieldName: String,
  idStringValue: Option[String],
  filterId: Option[BSONObjectID],
  mlModelId: BSONObjectID,
  predictAhead: Int,
  windowSize: Int,
  featuresNormalizationType: Option[VectorScalerType.Value],
  outputNormalizationType: Option[VectorScalerType.Value],
  pcaDims: Option[Int],
  trainingTestSplitRatio: Option[Double],
  trainingTestSplitOrderValue: Option[Double],
  repetitions: Option[Int],
  crossValidationFolds: Option[Int],
  crossValidationMinTrainingSizeRatio: Option[Double],
  crossValidationEvalMetric: Option[RegressionEvalMetric.Value]
) {
  def learningSetting =
    LearningSetting[RegressionEvalMetric.Value](featuresNormalizationType, pcaDims, trainingTestSplitRatio, Nil, repetitions, crossValidationFolds, crossValidationEvalMetric)
}