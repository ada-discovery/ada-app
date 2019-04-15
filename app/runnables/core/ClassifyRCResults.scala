package runnables.core

import javax.inject.Inject
import org.incal.core.dataaccess.Criterion._
import org.ada.server.models.Filter
import models.AdaException
import org.incal.core.{FilterCondition, InputFutureRunnable}
import org.incal.core.util.seqFutures
import org.incal.spark_ml.MLResultUtil
import org.incal.spark_ml.models.setting.{ClassificationLearningSetting, ClassificationRunSpec, IOSpec}
import persistence.RepoTypes.ClassifierRepo
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
    classificationRepo: ClassifierRepo
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
          // IO
          val ioSpec = IOSpec(
            weightsFieldNames,
            spec.outputFieldName,
            filter.map(_._id.get),
            spec.replicationFilterId
          )

          val runSpec = ClassificationRunSpec(ioSpec, spec.mlModelId, spec.learningSetting)

          val selectedFields = spec.learningSetting.featuresSelectionNum.map { featuresSelectionNum =>
            val inputFields = fields.filter(!_.name.equals(ioSpec.outputFieldName))
            val outputField = fields.find(_.name.equals(ioSpec.outputFieldName)).get
            val selectedInputFields = statsService.selectFeaturesAsAnovaChiSquare(jsons, inputFields, outputField, featuresSelectionNum)
            selectedInputFields ++ Seq(outputField)
          }.getOrElse(
            fields
          )

          val fieldNameAndSpecs = selectedFields.map(field => (field.name, field.fieldTypeSpec))
          mlService.classifyStatic(jsons, fieldNameAndSpecs, spec.outputFieldName, mlModel, runSpec.learningSetting).map { resultsHolder =>
            val finalResult = MLResultUtil.createStandardClassificationResult(runSpec, MLResultUtil.calcMetricStats(resultsHolder.performanceResults), Nil)
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
      case Some(filter) => FieldUtil.toDataSetCriteria(dsa.fieldRepo, filter.conditions)
      case None => Future(Nil)
    }
}

case class ClassifyRCResultsSpec(
  resultsDataSetId: Option[String],
  rcWeightDataSetIdPrefix: Option[String],
  rcWeightDataSetIdSuffixFrom: Option[Int],
  rcWeightDataSetIdSuffixTo: Option[Int],
  mlModelId: BSONObjectID,
  outputFieldName: String,
  filterName: Option[String],
  replicationFilterId: Option[BSONObjectID],
  learningSetting: ClassificationLearningSetting
)