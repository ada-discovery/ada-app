package runnables.mpower

import javax.inject.Inject
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json._
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.ada.server.services.DataSetService
import org.ada.server.dataaccess.RepoTypes.JsonCrudRepo
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.incal.core.dataaccess.Criterion._
import org.incal.core.util.seqFutures
import org.ada.server.AdaException
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.server.dataaccess.ignite.BinaryJsonUtil.getValueFromJson

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf
import scala.util.Random
import play.api.Logger

class CheckConsistencyOfAllInOneFeatureSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnableExt[CheckConsistencyOfAllInOneFeatureSetSpec] {

  private val submissionIdFieldName = "submissionId"
  private val logger = Logger

  override def runAsFuture(spec: CheckConsistencyOfAllInOneFeatureSetSpec) = {
    def keyValue(json: JsObject): Any = {
      val keyJsValue = (json \ spec.keyFieldName)
      if (spec.isKeyInt) keyJsValue.as[Int] else keyJsValue.as[String]
    }

    for {
      // data set accessors
      scoreBoardDsa <- dsaf.getOrError(spec.scoreBoardDataSetId)
      scoreBoardDataSetRepo = scoreBoardDsa.dataSetRepo

      allInOneDsa <- dsaf.getOrError(spec.allInOneFeatureSetId)
      allInOneDataSetRepo = allInOneDsa.dataSetRepo

      // retrieve all the submission ids
      submissionJsons <- scoreBoardDataSetRepo.find(projection = Seq(submissionIdFieldName))

      // create submission id data set repo pairs
      submissionIdDataRepos <- seqFutures(submissionJsons)(submissionIdDataSetRepo(spec.submissionDataSetPrefix))

      // randomly draw n all-in-one features
      randomAllInOneFeatures <- allInOneDataSetRepo.find(projection = Seq(spec.keyFieldName)).flatMap { jsonKeys =>
        logger.info(s"Loading ${spec.numberOfRecordsToCheck} random entries.")
        val keys = jsonKeys.map(keyValue)
        val randomKeys = Random.shuffle(keys).take(spec.numberOfRecordsToCheck)
        allInOneDataSetRepo.find(Seq(spec.keyFieldName #-> randomKeys.toSeq))
      }

      // check the features for all submission data sets available
      _ <- seqFutures(randomAllInOneFeatures.toSeq.zipWithIndex) { case (json, index) =>
        val key = keyValue(json)

        logger.info(s"Checking consistency of json $index with the key $key.")

        seqFutures(submissionIdDataRepos.flatten) { case (submissionId, dataSetRepo) =>
          checkFeatures(json, submissionId, dataSetRepo, spec.keyFieldName, key)
        }
      }
    } yield
      ()
  }

  private def submissionIdDataSetRepo(
    submissionDataSetPrefix: String)(
    submissionJson: JsObject
  ): Future[Option[(String, JsonCrudRepo)]] = {
    val submissionId = (submissionJson \ submissionIdFieldName).toOption.flatMap(_ match {
      case JsNull => None
      case submissionIdJsValue: JsValue =>
        val id = submissionIdJsValue.asOpt[String].getOrElse(submissionIdJsValue.as[Int].toString)
        Some(id)
    })

    submissionId.map { submissionId =>
      dsaf(submissionDataSetPrefix + "." + submissionId).map(_.map(dsa =>
        (submissionId, dsa.dataSetRepo)
      ))
    }.getOrElse(
      Future(None)
    )
  }

  private def checkFeatures(
    allInOneFeatureJson: JsObject,
    submissionId: String,
    featureDataSetRepo: JsonCrudRepo,
    keyFieldName: String,
    keyValue: Any
  ): Future[Unit] = {
    val allInOneFeatureNameJsValueMap = allInOneFeatureJson.fields.filter(_._1.startsWith(submissionId + "-")).map {
      case (fieldName, jsValue) => (fieldName.stripPrefix(submissionId + "-"), jsValue)
    }.toMap

    for {
      featureJson <- featureDataSetRepo.find(Seq(keyFieldName #== keyValue), limit = Some(1)).flatMap { featureJsons =>
        if (featureJsons.size > 0)
          Future(featureJsons.head)
        else
          featureDataSetRepo.find(Seq(keyFieldName.toLowerCase #== keyValue), limit = Some(1)).map(_.head)
      }
    } yield
      featureJson.fields.filterNot{ case (fieldName, _) => fieldName.equalsIgnoreCase(keyFieldName) || fieldName.equals(JsObjectIdentity.name) }.map { case (fieldName, jsValue) =>
        val value = getValueFromJson(jsValue)
        allInOneFeatureNameJsValueMap.get(fieldName).map { allInOneJsValue =>
          val allInOneValue = getValueFromJson(allInOneJsValue)
          if (!value.equals(allInOneValue))
            throw new AdaException(s"Found mismatch in the submission $submissionId, field name $fieldName: $value vs $allInOneValue")
        }.getOrElse(
          throw new AdaException(s"Found mismatch in the submission $submissionId, field name $fieldName not found in the all-in-one feature set.")
        )
      }
  }
}

case class CheckConsistencyOfAllInOneFeatureSetSpec(
  scoreBoardDataSetId: String,
  submissionDataSetPrefix: String,
  allInOneFeatureSetId: String,
  keyFieldName: String,
  numberOfRecordsToCheck: Int,
  isKeyInt: Boolean
)