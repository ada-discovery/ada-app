package runnables

import java.{lang, util => ju}
import javax.inject.Inject

import dataaccess.AscSort
import models.DataSetFormattersAndIds.JsObjectIdentity

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import _root_.util.{JsonUtil, seqFutures}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import dataaccess.Criterion.Infix
import models._
import play.api.libs.json._
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID
import services.DataSetService

import scala.concurrent.{Await, Future}

class CreateMPowerDistanceDataSet @Inject() (
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends Runnable {

  private val dataSetId = "mpower_challenge.walking_activity_training"
  private val dsa = dsaf(dataSetId).get
  private val dataSetRepo = dsa.dataSetRepo
  private val fieldRepo = dsa.fieldRepo

  private val normDataSetId = "mpower_challenge.walking_activity_training_norms"
  private val normDataSetName = "Walking Activity Training Norms"

  private def normDataSetSetting = DataSetSetting(
    None,
    normDataSetId,
    "_id",
    None,
    None,
    None,
    "medTimepoint",
    None,
    None,
    false,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  private val timeout = 30 hours

  private val motionFields = Seq("attitude", "rotationRate", "userAcceleration", "gravity")

  private val acceleremoterPaths = Seq(
    "accel_walking_outboundu002ejsonu002eitems",
    "accel_walking_restu002ejsonu002eitems",
    "accel_walking_returnu002ejsonu002eitems"
  )

  private val coreMotionPaths = Seq(
    "deviceMotion_walking_outboundu002ejsonu002eitems",
    "deviceMotion_walking_restu002ejsonu002eitems",
    "deviceMotion_walking_returnu002ejsonu002eitems"
  )

  private val motionPaths = coreMotionPaths.map ( corePath =>
    motionFields.map(corePath + "." + _)
  ).flatten

  private val paths = acceleremoterPaths ++ motionPaths
  private val corePathsSet = (acceleremoterPaths ++ coreMotionPaths).toSet

  private val idName = JsObjectIdentity.name
  private val batchSize = 10

  override def run = {
    val future = for {
      // get the data set meta info
      metaInfo <- dsa.metaInfo

      // register the norm data set (if not registered already)
      newDsa <- dsaf.register(
        metaInfo.copy(_id = None, id = normDataSetId, name = normDataSetName, timeCreated = new ju.Date()),
        Some(normDataSetSetting),
        None
      )

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

      // retrieve the total count
      count <- dataSetRepo.count()

      // delete all from the old data set
      _ <- newDsa.dataSetRepo.deleteAll

      // get all the ids
      ids <- dataSetRepo.find(
        projection = Seq(idName),
        sort = Seq(AscSort(idName))
      ).map(_.map(json => (json \ idName).as[BSONObjectID]))

      // process and save jsons
      _ <- createNormsAndSaveDataSet(newDsa, ids.toSeq)
    } yield
      ()

    Await.result(future, timeout)
  }

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

object CreateMPowerDistanceDataSet extends GuiceBuilderRunnable[CreateMPowerDistanceDataSet] with App { run }
