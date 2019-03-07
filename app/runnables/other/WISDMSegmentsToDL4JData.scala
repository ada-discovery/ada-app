package runnables.other

import java.nio.file.{Files, Paths}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import dataaccess.StreamSpec
import javax.inject.Inject
import models.{Field, FieldTypeId}
import models.ml.DerivedDataSetSpec
import org.incal.core.InputFutureRunnable
import org.incal.core.dataaccess.Criterion._
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.{JsNull, JsNumber, JsObject}
import services.DataSetService
import dataaccess.JsonReadonlyRepoExtra._
import models.DataSetFormattersAndIds.JsObjectIdentity
import org.incal.core.util.writeStringAsStream
import util.FieldUtil._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class WISDMSegmentsToDL4JData @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService
) extends InputFutureRunnable[WISDMSegmentsToDL4JDataSpec] {

  private val logger = Logger

  private object FieldName {
    val timeStamp = "timestamp"
    val userId = "user"
    val activity = "activity"
    val sessionId = "sessionId"
    val segmentId = "segmentId"
    val xAcceleration = "x_accel"
    val yAcceleration = "y_accel"
    val zAcceleration = "z_accel"
  }

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: WISDMSegmentsToDL4JDataSpec) = {
    val dsa = dsaf(input.sourceDataSetId).get

    val header = Seq(FieldName.xAcceleration, FieldName.yAcceleration, FieldName.zAcceleration).mkString(input.delimiter)

    for {
      // user ids
      userIds <- dsa.dataSetRepo.find(projection = Seq(FieldName.userId)).map(_.map(json => (json \ FieldName.userId).as[Int]).toSet)

      // activities
      activityField <- dsa.fieldRepo.get(FieldName.activity).map(_.get)
      activities = activityField.numValues.map(_.map(_._1.toInt)).get

      // max session id
      maxSessionId <- dsa.dataSetRepo.max(FieldName.sessionId).map(_.get.as[Int])

      // max segment id
      maxSegmentId <- dsa.dataSetRepo.max(FieldName.segmentId).map(_.get.as[Int])

      segmentIds = for { x <- userIds; y <- activities; z <- 1 to maxSessionId; w <- 0 to maxSegmentId } yield (x, y, z, w)

      // user-id-activity-session as a source
      segmentSource: Source[(Int, Int, Int, Int), NotUsed] = Source.fromIterator(() => segmentIds.toIterator)

      // stream of new jsons updated in a given order
      newSource: Source[(Seq[Seq[Double]], Int, Int), NotUsed] = {
        segmentSource.mapAsync(1) { case (userId, activity, sessionId, segmentId) =>
          dsa.dataSetRepo.find(
            Seq(FieldName.userId #== userId, FieldName.activity #== activity, FieldName.sessionId #== sessionId, FieldName.segmentId #== segmentId)
          ).map { segmentJsons =>
            logger.info(s"Processing ${segmentJsons.size} jsons for the user '$userId' and activity $activity and session $sessionId.")

            val features = segmentJsons.map { json =>
              val timeStamp = (json \ FieldName.timeStamp).as[Long]

              val xAccel = asDouble(json, FieldName.xAcceleration)
              val yAccel = asDouble(json, FieldName.yAcceleration)
              val zAccel = asDouble(json, FieldName.zAcceleration)

              (timeStamp, Seq(xAccel, yAccel, zAccel))
            }.toSeq.sortBy(_._1).map(_._2)

            (features, activity, userId)
          }
        }
      }
    } yield {
      var fileIndex = input.startingFileIndex.getOrElse(0)

      newSource.runForeach { case (features, output, userId) =>
        val seriesContent = Seq(header ++ features.map(_.mkString(input.delimiter))).mkString("\n")

        println(fileIndex)

        writeAux(input.outputFolderName, "features", fileIndex + ".csv", seriesContent)
        writeAux(input.outputFolderName, FieldName.activity, fileIndex + ".csv", output.toString)
        writeAux(input.outputFolderName, FieldName.userId, fileIndex + ".csv", userId.toString)
        fileIndex += 1
      }
    }
  }

  private def writeAux(outputFolderName: String, folderName: String, fileName: String, content: String) = {
    val folder = outputFolderName + "/" + folderName
    val path = Paths.get(folder)

    if (!Files.exists(path)) {
      Files.createDirectory(path)
    }
    writeStringAsStream(content, new java.io.File(s"$folder/$fileName"))
  }

  private def asDouble(json: JsObject, fieldName: String) =
    (json \ fieldName).as[Double]

  override def inputType = typeOf[WISDMSegmentsToDL4JDataSpec]
}

case class WISDMSegmentsToDL4JDataSpec(
  sourceDataSetId: String,
  delimiter: String,
  startingFileIndex: Option[Int],
  outputFolderName: String
)
