package runnables.mpower

import javax.inject.Inject
import org.ada.server.models.{Field, FieldTypeId}
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json._
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.incal.core.util.seqFutures
import org.ada.server.services.DataSetService
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class CheckFeatureDiscrepancies @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnableExt[CheckFeatureDiscrepanciesSpec] {

  private val logger = Logger

  private val submissionIdFieldName = "submissionId"
  private val featureNumField = Field("featureNum", Some("Feature Num"), FieldTypeId.Integer)

  override def runAsFuture(spec: CheckFeatureDiscrepanciesSpec) =
    for {
      // data set accessors
      submissionMetaDataDsa <- dsaf.getOrError(spec.submissionMetaDataSetId)
      submissionTemplateDsa <- dsaf.getOrError(spec.submissionTemplateDataSetId)

      // retrieve all the submission meta data
      submissionMetaInfos <- submissionMetaDataDsa.dataSetRepo.find()

      // retrieve all the submission meta data
      submissionTemplateJsons <- submissionTemplateDsa.dataSetRepo.find(projection = Seq(spec.keyFieldName))

      // extract the template submission keys
      submissionTemplateKeys = submissionTemplateJsons.map { json =>
        val jsValue = (json \ spec.keyFieldName)
        jsValue.asOpt[String].getOrElse(jsValue.asOpt[Int].toString)
      }.toSet

      // check the record keys of all the submissions
      _ <- seqFutures(submissionMetaInfos)(
        checkFeatureKeys(spec.submissionDataSetPrefix, submissionTemplateKeys, spec.keyFieldName)
      )
    } yield
      ()

  private def checkFeatureKeys(
    submissionDataSetPrefix: String,
    templateSubmissionKeys: Set[String],
    keyFieldName: String)(
    submissionMetaJson: JsObject
  ): Future[Unit] = {
    val submissionId = (submissionMetaJson \ submissionIdFieldName).get match {
      case JsNull => None
      case submissionIdJsValue: JsValue =>
        val id = submissionIdJsValue.asOpt[String].getOrElse(submissionIdJsValue.as[Int].toString)
        Some(id)
    }

    val unitFuture = Future(())

    def checkAux(featureSetDsa: DataSetAccessor) = {
      for {
        jsons <- featureSetDsa.dataSetRepo.find(projection = Seq(keyFieldName))
      } yield {
        val submissionKeys = jsons.map { json =>
          val jsValue = (json \ keyFieldName)
          jsValue.asOpt[String].getOrElse(jsValue.asOpt[Int].toString)
        }.toSet
        val dif1 = templateSubmissionKeys -- submissionKeys
        val dif2 = submissionKeys -- templateSubmissionKeys
        if (dif1.size == 0 && dif2.size == 0) {
          logger.info(s"Submission $submissionId is OK.")
        } else {
          logger.info(s"Submission $submissionId has ${submissionKeys.size} out of which ${dif2.size} are EXTRA!")
          val output = if (dif2.size <= 10) dif2.mkString(",") else dif2.take(10).mkString(",") + ".."
          logger.info(output)
        }
      }
    }

    submissionId.map { submissionId =>
      dsaf(submissionDataSetPrefix + "." + submissionId).flatMap(
        _.map(checkAux).getOrElse(unitFuture)
      )
    }.getOrElse(
      unitFuture
    )
  }
}

case class CheckFeatureDiscrepanciesSpec(
  submissionMetaDataSetId: String,
  submissionTemplateDataSetId: String,
  submissionDataSetPrefix: String,
  keyFieldName: String
)