package runnables.core

import javax.inject.Inject

import dataaccess.RepoTypes.FieldRepo
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.core.FilterCondition.toCriterion
import models.{AdaException, Filter}
import org.incal.core.{FilterCondition, InputFutureRunnable}
import org.incal.core.util.seqFutures
import org.incal.spark_ml.MachineLearningUtil
import org.incal.spark_ml.models.classification.ClassificationEvalMetric
import org.incal.spark_ml.models.results.ClassificationSetting
import org.incal.spark_ml.models.VectorScalerType
import persistence.RepoTypes.ClassificationRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import services.DataSetService
import services.ml.MachineLearningService
import services.stats.StatsService
import util.FieldUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class ClassifyRCResults @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    statsService: StatsService,
    dataSetService: DataSetService,
    classificationRepo: ClassificationRepo
  ) extends InputFutureRunnable[ClassifyRCResultsSpec] {

  private val logger = Logger // (this.getClass())

  private val dataSetFieldName = "inputOutputSpec-resultDataSetId"

  override def runAsFuture(input: ClassifyRCResultsSpec) =
    for {
      // get the data set ids
      dataSetIds <- dataSetIds(input)

      // clasify data sets one-by-one
      _ <- seqFutures(dataSetIds) { classify(_, input) }
    } yield
      ()

  private def dataSetIds(input: ClassifyRCResultsSpec) = {
    def resultsDataSetIds(dataSetId: String) = {
      val dsa = dsaf(dataSetId).getOrElse(
        throw new AdaException(s"Data set ${dataSetId} not found.")
      )

      for {
        jsons <- dsa.dataSetRepo.find(projection = Seq(dataSetFieldName))
      } yield
        jsons.map { json =>
          (json \ dataSetFieldName).get.as[String]
        }.toSeq.sorted
      }

    (
      input.rcWeightDataSetIdPrefix,
      input.rcWeightDataSetIdSuffixFrom,
      input.rcWeightDataSetIdSuffixTo
    ).zipped.headOption.map { case (dataSetIdPrefix, from, to) =>
      Future((from to to).map(dataSetIdPrefix + _).sorted)
    }.getOrElse(
      resultsDataSetIds(input.resultsDataSetId.getOrElse(
        throw new AdaException("Results data set id or RC weight data set id (with suffix from-to) expected.")
      ))
    )
  }

  private def classify(dataSetId: String, spec: ClassifyRCResultsSpec): Future[Unit] = {
    val dsa = dsaf(dataSetId).getOrElse(
      throw new AdaException(s"Data set $dataSetId not found.")
    )

    logger.info(s"Classifying RC weight data set $dataSetId.")

    val mlModelFuture = classificationRepo.get(spec.mlModelId)
    val filterFuture = spec.filterName match {
      case Some(filterName) =>
        dsa.filterRepo.find(Seq("name" #== Some(filterName))).map(_.headOption)
      case None =>
        Future(None)
    }
    val allFieldsFuture = dsa.fieldRepo.find()

    for {
      // get a classification ml model
      mlModel <- mlModelFuture

      // get a filter (if any)
      filter <- filterFuture

      // get all the fields
      allFields <- allFieldsFuture

      // filter the weight fields
      weightsFieldNames = allFields.filter(_.name.startsWith("rc_w_")).map(_.name).toSeq

      // prepare filter criteria
      criteria <- loadCriteria(dsa, filter)

      // load the data
      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, weightsFieldNames ++ Seq(spec.outputFieldName), criteria)

      // classify and save the result
      _ <- mlModel match {
        case Some(mlModel) =>
          val setting = ClassificationSetting(
            spec.mlModelId,
            spec.outputFieldName,
            weightsFieldNames,
            filter.map(_._id.get),
            spec.featuresNormalizationType,
            spec.featuresSelectionNum,
            spec.pcaDims,
            spec.trainingTestingSplit,
            spec.replicationFilterId,
            spec.samplingOutputValues.zip(spec.samplingRatios),
            spec.repetitions,
            spec.crossValidationFolds,
            spec.crossValidationEvalMetric,
            spec.binCurvesNumBins
          )

          val selectedFields = spec.featuresSelectionNum.map { featuresSelectionNum =>
            val inputFields = fields.filter(!_.name.equals(setting.outputFieldName))
            val outputField = fields.find(_.name.equals(setting.outputFieldName)).get
            val selectedInputFields = statsService.selectFeaturesAsAnovaChiSquare(jsons, inputFields, outputField, featuresSelectionNum)
            selectedInputFields ++ Seq(outputField)
          }.getOrElse(
            fields
          )

          val fieldNameAndSpecs = selectedFields.map(field => (field.name, field.fieldTypeSpec))
          mlService.classify(jsons, fieldNameAndSpecs, spec.outputFieldName, mlModel, setting.learningSetting).map { resultsHolder =>
            val finalResult = MachineLearningUtil.createClassificationResult(setting, resultsHolder.performanceResults, Nil)
            dsa.classificationResultRepo.save(finalResult)
          }

        case None => Future(())
      }
    } yield
      ()
  }

  override def inputType = typeOf[ClassifyRCResultsSpec]

  private def loadCriteria(dsa: DataSetAccessor, filter: Option[Filter]) =
    filter match {
      case Some(filter) => toDataSetCriteria(dsa.fieldRepo, filter.conditions)
      case None => Future(Nil)
    }

  private def toDataSetCriteria(
    fieldRepo: FieldRepo,
    conditions: Seq[FilterCondition]
  ): Future[Seq[Criterion[Any]]] =
    for {
      valueConverters <- {
        val fieldNames = conditions.map(_.fieldName)
        FieldUtil.valueConverters(fieldRepo, fieldNames)
    }
  } yield
    conditions.map(toCriterion(valueConverters)).flatten
}

case class ClassifyRCResultsSpec(
  resultsDataSetId: Option[String],
  rcWeightDataSetIdPrefix: Option[String],
  rcWeightDataSetIdSuffixFrom: Option[Int],
  rcWeightDataSetIdSuffixTo: Option[Int],
  mlModelId: BSONObjectID,
  outputFieldName: String,
  filterName: Option[String],
  featuresNormalizationType: Option[VectorScalerType.Value],
  featuresSelectionNum: Option[Int],
  pcaDims: Option[Int],
  trainingTestingSplit: Option[Double],
  replicationFilterId: Option[BSONObjectID],
  samplingOutputValues: Seq[String],
  samplingRatios: Seq[Double],
  repetitions: Option[Int],
  crossValidationFolds: Option[Int],
  crossValidationEvalMetric: Option[ClassificationEvalMetric.Value],
  binCurvesNumBins: Option[Int]
)