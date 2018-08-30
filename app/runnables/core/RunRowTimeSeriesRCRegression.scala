package runnables.core

import java.{lang => jl}
import javax.inject.Inject

import com.banda.math.domain.rand.RandomDistribution
import com.banda.network.domain.ActivationFunctionType
import field.FieldTypeHelper
import models.DataSetFormattersAndIds.FieldIdentity
import models.ml.classification.ValueOrSeq.ValueOrSeq
import models.ml.timeseries.ReservoirSpec
import models.ml.{IOJsonTimeSeriesSpec, LearningSetting, RegressionEvalMetric, VectorTransformType}
import persistence.RepoTypes.RegressionRepo
import persistence.dataset.DataSetAccessorFactory
import reactivemongo.bson.BSONObjectID
import runnables.InputFutureRunnable
import services.ml.MachineLearningService
import org.incal.core.dataaccess.Criterion.Infix

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunRowTimeSeriesRCRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressionRepo
  ) extends InputFutureRunnable[RunRowTimeSeriesRCRegressionSpec] with TimeSeriesResultsHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(
    input: RunRowTimeSeriesRCRegressionSpec
  ): Future[Unit] = {
    println(input)

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
      orderedValues = input.orderedStringValues.map(orderFieldType.displayStringToValue)

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
          input.windowSize,
          Some(input.reservoirSpec),
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

  override def inputType = typeOf[RunRowTimeSeriesRCRegressionSpec]
}

case class RunRowTimeSeriesRCRegressionSpec(
  // input/output specification
  dataSetId: String,
  inputFieldNames: Seq[String],
  outputFieldName: String,
  orderFieldName: String,
  orderedStringValues: Seq[String],
  criterionFieldName: String,
  criterionStringValue: String,
  predictAhead: Int,

  // ML model
  mlModelId: BSONObjectID,

  // delay line window size
  windowSize: Option[Int],

  // reservoir setting
  reservoirNodeNum: ValueOrSeq[Int] = Left(None),
  reservoirInDegree: ValueOrSeq[Int] = Left(None),
  reservoirEdgesNum: ValueOrSeq[Int] = Left(None),
  reservoirPreferentialAttachment: Boolean = false,
  reservoirBias: Boolean = false,
  reservoirCircularInEdges: Option[Seq[Int]] = None,
  inputReservoirConnectivity: ValueOrSeq[Double] = Left(None),
  reservoirSpectralRadius: ValueOrSeq[Double] = Left(None),
  reservoirFunctionType: ActivationFunctionType,
  reservoirFunctionParams: Seq[Double] = Nil,
  washoutPeriod: ValueOrSeq[Int] = Left(None),

  // cross-validation
  crossValidationFolds: Option[Int],
  crossValidationMinTrainingSize: Option[Double],
  crossValidationEvalMetric: Option[RegressionEvalMetric.Value],

  // pre-processing and other stuff
  featuresNormalizationType: Option[VectorTransformType.Value],
  pcaDims: Option[Int],
  trainingTestingSplit: Option[Double],
  replicationItemId: Option[BSONObjectID],
  repetitions: Option[Int]
) {
  def learningSetting =
    LearningSetting[RegressionEvalMetric.Value](featuresNormalizationType, pcaDims, trainingTestingSplit, Nil, repetitions, crossValidationFolds, crossValidationEvalMetric)

  def reservoirSpec =
    ReservoirSpec(
      inputNodeNum = pcaDims.getOrElse(inputFieldNames.size) * windowSize.getOrElse(1),
      bias = 1,
      nonBiasInitial = 0,
      reservoirNodeNum = reservoirNodeNum,
      reservoirInDegree = reservoirInDegree,
      reservoirEdgesNum = reservoirEdgesNum,
      reservoirPreferentialAttachment = reservoirPreferentialAttachment,
      reservoirBias = reservoirBias,
      reservoirCircularInEdges = reservoirCircularInEdges,
      inputReservoirConnectivity = inputReservoirConnectivity,
      reservoirSpectralRadius = reservoirSpectralRadius,
      reservoirFunctionType = reservoirFunctionType,
      reservoirFunctionParams = reservoirFunctionParams,
      weightDistribution = RandomDistribution.createNormalDistribution(classOf[jl.Double], 0d, 1d),
      washoutPeriod = washoutPeriod
    )
}