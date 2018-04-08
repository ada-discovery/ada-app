package runnables.mpower

import javax.inject.Inject

import models.DataSetFormattersAndIds.FieldIdentity
import models.{AdaException, Field, FieldTypeId, StorageType}
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}
import runnables.InputFutureRunnable
import services.DataSetService
import dataaccess.Criterion._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.reflect.runtime.universe.typeOf

class LinkMDSProjectionWithFeatureInfo @Inject()(
    dataSetService: DataSetService,
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[LinkMDSProjectionWithFeatureInfoSpec] {

  private val submissionIdFieldName = "SubmissionId"
  private val featureFieldName = "Name"

  private val scoreSubmissionIdFieldName = "submissionId"

  private val newMdsX1Field = Field("mds_x1", Some("MDS X1"), FieldTypeId.Double, false)
  private val newMdsX2Field = Field("mds_x2", Some("MDS X2"), FieldTypeId.Double, false)

  private val excludedScoreFields = Seq(
    "ROW_ID", "ROW_VERSION", "Team", "dataFileHandleId", "entityId", "submissionName", "teamWiki"
  )

  override def runAsFuture(input: LinkMDSProjectionWithFeatureInfoSpec) = {
    val metaInfoDsa = dsaf(input.featureMetaInfoDataSetId).get
    val scoreDsa = dsaf(input.scoreDataSetId).get

    for {
      // get the name of the source score data set
      dataSetName <- metaInfoDsa.dataSetName

      // get all the fields
      fields <- metaInfoDsa.fieldRepo.find()

      // get all the views
      views <- metaInfoDsa.dataViewRepo.find()

      // original data set setting
      setting <- metaInfoDsa.setting

      // register target dsa
      targetDsa <- dataSetService.register(
        metaInfoDsa,
        input.newMDSDataSetId,
        dataSetName + " MDS",
        StorageType.ElasticSearch,
        setting.defaultDistributionFieldName
      )

      // create a submission feature name -> json map
      submissionFeatureNameJsonMap <- metaInfoDsa.dataSetRepo.find().map { jsons =>
        jsons.map { json =>
          val submissionId = (json \ submissionIdFieldName).as[Int]
          val featureName = (json \ featureFieldName).as[String]
          (submissionId + "-" + featureName, json)
        }.toMap
      }

      // retrieve the score fields that we want to pass on
      scoreFields <- scoreDsa.fieldRepo.find(Seq(FieldIdentity.name #!-> excludedScoreFields))

      // create a submission id -> score json map
      submissionIdScoreJsonMap <- scoreDsa.dataSetRepo.find(projection = scoreFields.map(_.name)).map { jsons =>
        jsons.flatMap { json =>
          scoreSubmissionId(json).map(submissionId => (submissionId, json.-(scoreSubmissionIdFieldName)))
        }.toMap
      }

      // create new jsons with MDS projection (x1 and x2)
      newJsons = {
        val lines = Source.fromFile(input.mdsProjectionFileName).getLines()

        // header could be ignored
        val header = lines.take(1).toSeq.head

        lines.map { line =>
          val parts = line.split(",").map(_.trim)
          val featureName = parts(0)
          val submissionId =  featureName.split("-")(0).toInt
          val x1 = parts(1).toDouble
          val x2 = parts(2).toDouble

          val featureInfoJson = submissionFeatureNameJsonMap.get(featureName).getOrElse(
            throw new AdaException(s"Feature $featureName not found.")
          )

          val scoreJson = submissionIdScoreJsonMap.get(submissionId).getOrElse(
            throw new AdaException(s"Submission $submissionId not found.")
          )

          featureInfoJson ++ scoreJson ++ Json.obj(newMdsX1Field.name -> x1, newMdsX2Field.name -> x2)
        }.toSeq
      }

      // create a new dictionary
      _ <- {
        val scoreFieldsToStore = scoreFields.filter(_.name != scoreSubmissionIdFieldName)

        dataSetService.updateDictionaryFields(
          input.newMDSDataSetId,
          fields ++ scoreFieldsToStore ++ Seq(newMdsX1Field, newMdsX2Field),
          false,
          true
        )
      }

      // delete the old data (if any)
      _ <- targetDsa.dataSetRepo.deleteAll

      // save the new data
      _ <- targetDsa.dataSetRepo.save(newJsons)

      // save the views
      _ <- targetDsa.dataViewRepo.save(views)
    } yield
      ()
  }

  private def scoreSubmissionId(json: JsObject): Option[Int] =
    (json \ scoreSubmissionIdFieldName).toOption.flatMap( _ match {
      case JsNull => None
      case submissionIdJsValue: JsValue =>
        submissionIdJsValue.asOpt[Int] match {
          case Some(value) => Some(value)
          case None =>
            try {
              Some(submissionIdJsValue.as[String].toInt)
            } catch {
              case e: NumberFormatException => None
            }
        }
    })

  override def inputType = typeOf[LinkMDSProjectionWithFeatureInfoSpec]
}

case class LinkMDSProjectionWithFeatureInfoSpec(
  featureMetaInfoDataSetId: String,
  scoreDataSetId: String,
  newMDSDataSetId: String,
  mdsProjectionFileName: String
)