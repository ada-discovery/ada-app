package runnables.mpower

import javax.inject.Inject

import org.ada.server.models.{Field, FieldTypeId, StorageType}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json._
import services.DataSetService
import org.incal.core.InputFutureRunnable
import org.incal.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class AddFeatureNumToScoreBoard @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnable[AddFeatureNumToScoreBoardSpec] {

  private val submissionIdFieldName = "submissionId"
  private val featureNumField = Field("featureNum", Some("Feature Num"), FieldTypeId.Integer)

  override def runAsFuture(spec: AddFeatureNumToScoreBoardSpec) = {
    val dsa = dsaf(spec.scoreBoardDataSetId).get
    val newDataSetId = spec.scoreBoardDataSetId + "_ext"

    for {
      // get the name of the source score data set
      dataSetName <- dsa.dataSetName

      // retrieve all the score data
      jsons <- dsa.dataSetRepo.find()

      // create new jsons with feature num
      newJsons <- seqFutures(jsons)(createNewJsonWithFeatureNum(spec.submissionDataSetPrefix))

      // get all the fields
      fields <- dsa.fieldRepo.find()

      // get all the views
      views <- dsa.dataViewRepo.find()

      // original data set setting
      setting <- dsa.setting

      // register target dsa
      targetDsa <- dataSetService.register(
        dsa,
        newDataSetId,
        dataSetName + " Ext",
        StorageType.ElasticSearch
      )

      // create a new dictionary
      _ <- dataSetService.updateDictionaryFields(newDataSetId, fields ++ Seq(featureNumField), false, true)

      // delete the old data (if any)
      _ <- targetDsa.dataSetRepo.deleteAll

      // save the new data
      _ <- targetDsa.dataSetRepo.save(newJsons)

      // save the views
      _ <- targetDsa.dataViewRepo.save(views)
    } yield
      ()
  }

  private def createNewJsonWithFeatureNum(submissionDataSetPrefix: String)(json: JsObject): Future[JsObject] = {
    val submissionId = (json \ submissionIdFieldName).get match {
      case JsNull => None
      case submissionIdJsValue: JsValue =>
        val id = submissionIdJsValue.asOpt[String].getOrElse(submissionIdJsValue.as[Int].toString)
        Some(id)
    }

    val defaultJsonFuture = Future(json + (featureNumField.name, JsNull))

    submissionId.map { submissionId =>
      dsaf(submissionDataSetPrefix + "." + submissionId) match {
        case Some(featureSetDsa) =>
          featureSetDsa.fieldRepo.count().map { count =>
            val featureNumJson = JsNumber(count - 1)
            (json + (featureNumField.name, featureNumJson))
          }
        case None => defaultJsonFuture
      }
    }.getOrElse(defaultJsonFuture)
  }

  override def inputType = typeOf[AddFeatureNumToScoreBoardSpec]
}

case class AddFeatureNumToScoreBoardSpec(
  scoreBoardDataSetId: String,
  submissionDataSetPrefix: String
)