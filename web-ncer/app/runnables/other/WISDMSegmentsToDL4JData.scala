package runnables.other

import java.nio.file.{Files, Paths}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.incal.core.dataaccess.StreamSpec
import javax.inject.Inject
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.incal.core.dataaccess.Criterion._
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.{JsNull, JsNumber, JsObject}
import org.ada.server.services.DataSetService
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.incal.core.util.writeStringAsStream
import org.ada.server.field.FieldUtil._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class WISDMSegmentsToDL4JData @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService
) extends InputFutureRunnableExt[WISDMSegmentsToDL4JDataSpec] {

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
    val dsa = dsaf.applySync(input.sourceDataSetId).get

    val header = Seq(FieldName.xAcceleration, FieldName.yAcceleration, FieldName.zAcceleration).mkString(input.delimiter)

    for {
      // user ids
      userIds <- dsa.dataSetRepo.find(projection = Seq(FieldName.userId)).map(_.map(json => (json \ FieldName.userId).as[Int]).toSet)

      // activities
      activityField <- dsa.fieldRepo.get(FieldName.activity).map(_.get)
      activities = activityField.enumValues.map(_._1.toInt)

      // max session id
      maxSessionId <- dsa.dataSetRepo.max(FieldName.sessionId).map(_.get.as[Int])

      // max segment id
      maxSegmentId <- dsa.dataSetRepo.max(FieldName.segmentId).map(_.get.as[Int])

      segmentIds = for { x <- userIds.toSeq.sorted; y <- activities.toSeq.sorted; z <- 1 to maxSessionId; w <- 0 to maxSegmentId } yield (x, y, z, w)

      // user-id-activity-session as a source
      segmentSource: Source[(Int, Int, Int, Int), NotUsed] = Source.fromIterator(() => segmentIds.sortBy(_._1).toIterator)

      // stream of new jsons updated in a given order
      newSource: Source[(Seq[Seq[Double]], Int, Int), NotUsed] = {
        segmentSource.mapAsync(1) { case (userId, activity, sessionId, segmentId) =>
          dsa.dataSetRepo.find(
            Seq(FieldName.userId #== userId, FieldName.activity #== activity, FieldName.sessionId #== sessionId, FieldName.segmentId #== segmentId)
          ).map { segmentJsons =>
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
      }.filter(_._1.nonEmpty)
    } yield {
      var fileIndex = input.startingFileIndex.getOrElse(0)

      newSource.runForeach { case (features, activity, userId) =>
        logger.info(s"Processing ${features.size} features for the user '$userId' and activity $activity.")

        val seriesContent = (Seq(header) ++ features.map(_.mkString(input.delimiter))).mkString("\n")

        writeAux(input.outputFolderName, "features", fileIndex + ".csv", seriesContent)
        writeAux(input.outputFolderName, FieldName.activity, fileIndex + ".csv", activity.toString)
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
}

case class WISDMSegmentsToDL4JDataSpec(
  sourceDataSetId: String,
  delimiter: String,
  startingFileIndex: Option[Int],
  outputFolderName: String
)
