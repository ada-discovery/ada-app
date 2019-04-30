package runnables.mpower

import java.{util => ju}
import javax.inject.Inject

import org.ada.server.dataaccess.JsonUtil
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.server.models._
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.DataSetService
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.incal.core.FutureRunnable
import org.incal.play.GuiceRunnableApp
import org.incal.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateMPowerTestingDistanceDataSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val dataSetId = "mpower_challenge.walking_activity_testing"
  private val dsa = dsaf(dataSetId).get
  private val dataSetRepo = dsa.dataSetRepo
  private val fieldRepo = dsa.fieldRepo

  private val normDataSetId = "mpower_challenge.walking_activity_testing_norms"
  private val normDataSetName = "Walking Activity Testing Norms"
  private val motionFields = Seq("attitude", "rotationRate", "userAcceleration", "gravity")

  private val acceleremoterPaths = Seq(
    "accel_walking_outboundjsonitems",
    "accel_walking_restjsonitems",
    "accel_walking_returnjsonitems"
  )

  private val coreMotionPaths = Seq(
    "deviceMotion_walking_outboundjsonitems",
    "deviceMotion_walking_restjsonitems",
    "deviceMotion_walking_returnjsonitems"
  )

  private val motionPaths = coreMotionPaths.map ( corePath =>
    motionFields.map(corePath + "." + _)
  ).flatten

  private val paths = acceleremoterPaths ++ motionPaths
  private val corePathsSet = (acceleremoterPaths ++ coreMotionPaths).toSet

  private val idName = JsObjectIdentity.name
  private val batchSize = 10

  override def runAsFuture =
    for {
    // register the norm data set (if not registered already)
      newDsa <- dataSetService.register(dsa, normDataSetId, normDataSetName, StorageType.Mongo)

      // get all the fields
      fields <- fieldRepo.find()

      // update the dictionary
      _ <- {
        val strippedFields = fields.filterNot(field => corePathsSet.contains(field.name))

        val newFields = paths.map { path =>
          Seq(
            Field((path + "_manhattanNorms").replace('.', '_'), None, FieldTypeId.Double, true),
            Field((path + "_euclideanNorms").replace('.', '_'), None, FieldTypeId.Double, true)
          )
        }.flatten

        val fieldNameAndTypes = (strippedFields ++ newFields).map( field => (field.name, field.fieldTypeSpec))
        dataSetService.updateDictionary(normDataSetId, fieldNameAndTypes, false, true)
      }

      // delete all from the old data set
      _ <- newDsa.dataSetRepo.deleteAll

      // get all the ids
      ids <- dataSetRepo.allIds

      // process and save jsons
      _ <- createNormsAndSaveDataSet(newDsa, ids.toSeq)
    } yield
      ()

  private def createNormsAndSaveDataSet(
    newDsa: DataSetAccessor,
    ids: Seq[BSONObjectID]
  ) =
    seqFutures(ids.grouped(batchSize).zipWithIndex) {

      case (ids, groupIndex) =>
        Future.sequence(
          ids.map( id =>
            dataSetRepo.get(id)
          )
        ).map(_.flatten).flatMap { jsons =>
          //          dataSetRepo.find(
          //            criteria = Seq(idName #>= ids.head),
          //            limit = Some(batchSize),
          //            sort = Seq(AscSort(idName))
          //          )


          println(s"Processing time series ${groupIndex * batchSize} to ${(jsons.size - 1) + (groupIndex * batchSize)}")
          val newJsons = jsons.par.map { json =>
            def extractSeries(path: String) =
              JsonUtil.traverse(json, path).map(_.as[Double])

            val newFields = paths.par.map { path =>
              val xSeries = extractSeries(path + ".x")
              val ySeries = extractSeries(path + ".y")
              val zSeries = extractSeries(path + ".z")

              val series = Seq(xSeries, ySeries, zSeries).transpose

              val manhattanNorms = series.map( values =>
                JsNumber(values.map(Math.abs).sum)
              )

              val euclideanNorms = series.map( values =>
                JsNumber(Math.sqrt(values.map(value => value * value).sum))
              )

              Seq(
                (path + "_manhattanNorms").replace('.','_') -> JsArray(manhattanNorms),
                (path + "_euclideanNorms").replace('.','_') -> JsArray(euclideanNorms)
              )
            }.toList

            val strippedJsonValues: Seq[(String, JsValue)] = json.fields.filterNot{ case (fieldName, jsValue) =>
              corePathsSet.contains(fieldName)
            }

            JsObject(strippedJsonValues ++ newFields.flatten)
          }.toList

          // save the norm data set jsons
          dataSetService.saveOrUpdateRecords(newDsa.dataSetRepo, newJsons, None, false, None, Some(5))
        }
    }
}

object CreateMPowerTestingDistanceDataSet extends GuiceRunnableApp[CreateMPowerTestingDistanceDataSet]