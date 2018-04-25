package runnables.mpower

import javax.inject.Inject

import models.{Field, FieldTypeId, StorageType}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json._
import runnables.{FutureRunnable, InputFutureRunnable}
import services.DataSetService
import _root_.util.{GroupMapList, seqFutures}
import controllers.mpower.{FeatureMatrixExtractor, LDOPAScoreSubmissionInfo, SubmissionInfo, mPowerScoreSubmissionInfo}
import models.DataSetFormattersAndIds.FieldIdentity
import dataaccess.Criterion._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class CalcCorrelationAggregates @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnable[CalcCorrelationAggregatesSpec] with FeatureMatrixExtractor {

  private implicit val ldopaScoreSubmissionFormat = Json.format[LDOPAScoreSubmissionInfo]
  private implicit val mPowerScoreSubmissionFormat = Json.format[mPowerScoreSubmissionInfo]

  private val submissionIdFieldName = "submissionId"
  private val featureNumField = Field("featureNum", Some("Feature Num"), FieldTypeId.Integer)

  private val featureGroupSize = Some(200)

  private val logger = Logger

  override def runAsFuture(spec: CalcCorrelationAggregatesSpec) = {
    val scoreBoardDsa = dsaf(spec.scoreBoardDataSetId).get
    val correlationDsa = dsaf(spec.correlationDataSetId).get

    val newDataSetId = spec.scoreBoardDataSetId + "_ext"

    for {
      // get the name of the source score data set
      dataSetName <- scoreBoardDsa.dataSetName

      // create new jsons with feature num
      newJsons <- {
        calcCrossSubmissionMeanAbsCorrelations(scoreBoardDsa, correlationDsa).map { aggregates =>
          aggregates.groupBy(_._1).toSeq.sortBy(_._1).map { case (submissionId, aggregates) =>
            val sortedAggregates = aggregates.map { case (_, submissionId2, agg1, agg2) => (submissionId2, agg1, agg2) }.sortBy(_._1)
            sortedAggregates.map { case (submissionId2, agg1, agg2) =>

            }
          }

          Seq[JsObject]()
        }
      }

      // register target dsa
      targetDsa <- dataSetService.register(
        scoreBoardDsa,
        newDataSetId,
        dataSetName + " Ext",
        StorageType.ElasticSearch,
        ""
      )

      // create a new dictionary
      _ <- dataSetService.updateDictionaryFields(newDataSetId, Seq(featureNumField), false, true)

      // delete the old data (if any)
      _ <- targetDsa.dataSetRepo.deleteAll

      // save the new data
      _ <- targetDsa.dataSetRepo.save(newJsons)
    } yield
      ()
  }

  private def calcCrossSubmissionMeanAbsCorrelations(
    scoreBoardDsa: DataSetAccessor,
    correlationDsa: DataSetAccessor,
    featuresToExcludeFuture: Future[Map[Int, Set[String]]] = Future(Map())
  ): Future[Seq[(Int, Int, Option[Double], Option[Double])]] =
    for {
      // get all the scored submission infos
      submissionInfos <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map( json =>
          json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
        )
      )

      // features to exclude
      submissionFeaturesToExclude <- featuresToExcludeFuture

      // create a submission id feature names map
      submissionIdFeatureNamesMap <- createSubmissionIdFeatureMap(correlationDsa)

      // calculate abs correlation mean between each pair of submissions
      crossSubmissionMeanAbsCorrelations <- {
        val filteredFeatureNamesMap =
          submissionIdFeatureNamesMap.map { case (submissionId, features) =>
            val featuresToExclude = submissionFeaturesToExclude.get(submissionId).getOrElse(Set())
            val newFeatures = features.filterNot(featuresToExclude.contains)

            assert(
              featuresToExclude.size + newFeatures.size == features.size,
              s"The number of features after exclusion is inconsistent." +
                s"There must be some features that do not occur in the original feature set." +
                s"Counts: ${features.size} - ${featuresToExclude.size} != ${newFeatures.size}.\n" +
                s"Features to exclude: ${featuresToExclude.mkString(",")}\n" +
                s"Actual features: ${features.mkString(",")}"
            )

            (submissionId, newFeatures)
          }
        calcAbsCorrelationMeansForAllSubmissions(submissionInfos, filteredFeatureNamesMap, correlationDsa)
      }
    } yield
      crossSubmissionMeanAbsCorrelations

  private def calcAbsCorrelationMeansForAllSubmissions(
    submissionInfos: Traversable[SubmissionInfo],
    submissionIdFeatureNamesMap: Map[Int, Traversable[String]],
    corrDsa: DataSetAccessor
  ): Future[Seq[(Int, Int,  Option[Double], Option[Double])]] = {
    val definedSubmissionInfos = submissionInfos.filter(_.submissionIdInt.isDefined)

    logger.info(s"Calculating abs correlation means at the submission level for ${submissionInfos.size} submissions.")

    seqFutures(definedSubmissionInfos) { submissionInfo1 =>
      val submissionId1 = submissionInfo1.submissionIdInt.get
      val submissionInfos2 = definedSubmissionInfos.filter(subInfo => submissionId1 < subInfo.submissionIdInt.get)

      logger.info(s"Calculating abs correlation means for the submission ${submissionId1}.")

      seqFutures(submissionInfos2) { submissionInfo2 =>
        val submissionId2 = submissionInfo2.submissionIdInt.get
        calcAbsCorrelationMeansForSubmissionPair(submissionId1, submissionId2, submissionIdFeatureNamesMap, corrDsa)
      }
    }.map(_.flatten)
  }

  private def calcAbsCorrelationMeansForSubmissionPair(
    submissionId1: Int,
    submissionId2: Int,
    submissionIdFeatureNamesMap: Map[Int, Traversable[String]],
    corrDsa: DataSetAccessor
  ): Future[(Int, Int,  Option[Double], Option[Double])] = {

    // aux function to find feature names for submission ids
    def findFeatureNames(submissionId: Int): Traversable[String] =
      submissionIdFeatureNamesMap.get(submissionId).get

    val featureNames1 = findFeatureNames(submissionId1).toSeq
    val featureNames2 = findFeatureNames(submissionId2).toSeq

    for {
      absMeans <- extractFeatureMatrixAggregates(
        featureNames1, featureNames2, corrDsa, featureGroupSize, aggFun.mean, aggFun.max, Some(_.abs)
      )
    } yield
      (submissionId1, submissionId2, absMeans._1, absMeans._2)
  }

  private def createSubmissionIdFeatureMap(
    correlationDsa: DataSetAccessor
  ): Future[Map[Int, Traversable[String]]] =
    for {
    // get all the feature names
      featureNames <- correlationDsa.fieldRepo.find(Seq(FieldIdentity.name #!= featureFieldName)).map(fields =>
        fields.map(_.name)
      )
    } yield
      // create a submission id feature names map
      featureNames.map{ featureName =>
        val featureNameParts = featureName.split("-", 2)
        val submissionId = featureNameParts(0).toInt
        (submissionId, featureName)
      }.toGroupMap

  override def inputType = typeOf[CalcCorrelationAggregatesSpec]
}

case class CalcCorrelationAggregatesSpec(
  scoreBoardDataSetId: String,
  correlationDataSetId: String
)