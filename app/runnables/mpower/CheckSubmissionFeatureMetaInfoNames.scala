package runnables.mpower

import javax.inject.Inject

import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import org.incal.core.InputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.reflect.runtime.universe.typeOf

class CheckSubmissionFeatureMetaInfoNames @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[CheckSubmissionFeatureMetaInfoNamesSpec] {

  private val logger = Logger

  private val submissionIdFieldName = "SubmissionId"
  private val featureFieldName = "Name"

  override def runAsFuture(input: CheckSubmissionFeatureMetaInfoNamesSpec) = {
    val dsa = dsaf(input.featureMetaInfoDataSetId).get
    for {
      submissionFeatureSet <- dsa.dataSetRepo.find(projection = Seq(submissionIdFieldName, featureFieldName)).map { jsons =>
        jsons.map { json =>
          val submissionId = (json \ submissionIdFieldName).as[Int]
          val featureName = (json \ featureFieldName).as[String]
          submissionId + "-" + featureName
        }.toSet
      }
    } yield {
      val header = Source.fromFile(input.headerFileName).mkString
      val features = header.split(",").tail.sorted

      val unmatchedFound = features.filter { submissionFeatureName =>
        !submissionFeatureSet.contains(submissionFeatureName.trim)
      }

      if (features.size != submissionFeatureSet.size)
        logger.error(s"The number of features do not match: ${submissionFeatureSet.size} vs ${features.size}")

      if (unmatchedFound.nonEmpty) {
        logger.error(s"${unmatchedFound.size} unmatched entries found:")
        logger.error(unmatchedFound.mkString("\n"))
      }
    }
  }

  override def inputType = typeOf[CheckSubmissionFeatureMetaInfoNamesSpec]
}

case class CheckSubmissionFeatureMetaInfoNamesSpec(
  featureMetaInfoDataSetId: String,
  headerFileName: String
)