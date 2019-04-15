package runnables.mpower

import javax.inject.Inject

import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json._
import org.incal.core.InputFutureRunnable
import services.DataSetService
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
  ) extends InputFutureRunnable[CheckConsistencyOfAllInOneFeatureSetSpec] {

  private val submissionIdFieldName = "submissionId"
  private val logger = Logger

  override def runAsFuture(spec: CheckConsistencyOfAllInOneFeatureSetSpec) = {
    val scoreBoardDataSetRepo = dsaf(spec.scoreBoardDataSetId).get.dataSetRepo
    val allInOneDataSetRepo = dsaf(spec.allInOneFeatureSetId).get.dataSetRepo

    def keyValue(json: JsObject): Any = {
      val keyJsValue = (json \ spec.keyFieldName)
      if (spec.isKeyInt) keyJsValue.as[Int] else keyJsValue.as[String]
    }

    for {
      // retrieve all the submission ids
      submissionJsons <- scoreBoardDataSetRepo.find(projection = Seq(submissionIdFieldName))

      // create submission id data set repo pairs
      submissionIdDataRepos = submissionJsons.map(submissionIdDataSetRepo(spec.submissionDataSetPrefix)).flatten

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

        seqFutures(submissionIdDataRepos) { case (submissionId, dataSetRepo) =>
          checkFeatures(json, submissionId, dataSetRepo, spec.keyFieldName, key)
        }
      }
    } yield
      ()
  }

  private def submissionIdDataSetRepo(
    submissionDataSetPrefix: String)(
    submissionJson: JsObject
  ): Option[(String, JsonCrudRepo)] = {
    val submissionId = (submissionJson \ submissionIdFieldName).toOption.flatMap(_ match {
      case JsNull => None
      case submissionIdJsValue: JsValue =>
        val id = submissionIdJsValue.asOpt[String].getOrElse(submissionIdJsValue.as[Int].toString)
        Some(id)
    })

    submissionId.flatMap { submissionId =>
      dsaf(submissionDataSetPrefix + "." + submissionId).map(dsa =>
        (submissionId, dsa.dataSetRepo)
      )
    }
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

  override def inputType = typeOf[CheckConsistencyOfAllInOneFeatureSetSpec]
}

case class CheckConsistencyOfAllInOneFeatureSetSpec(
  scoreBoardDataSetId: String,
  submissionDataSetPrefix: String,
  allInOneFeatureSetId: String,
  keyFieldName: String,
  numberOfRecordsToCheck: Int,
  isKeyInt: Boolean
)