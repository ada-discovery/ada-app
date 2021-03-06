package runnables.other

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.incal.core.dataaccess.StreamSpec
import javax.inject.Inject
import org.ada.server.models.{Field, FieldTypeId}
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.incal.core.dataaccess.Criterion._
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.{JsNumber, JsObject}
import org.ada.server.services.DataSetService
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.server.field.FieldUtil._
import org.ada.server.models.datatrans.ResultDataSetSpec

import scala.concurrent.ExecutionContext.Implicits.global

class SegmentWISDMSessions @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService
) extends InputFutureRunnableExt[SegmentWISDMSessionsSpec] {

  private val logger = Logger

  private object FieldName {
    val timeStamp = "timestamp"
    val userId = "user"
    val activity = "activity"
    val sessionId = "sessionId"
  }

  private val newSegmentIdField = Field("segmentId", Some("Segment Id"), FieldTypeId.Integer)

  override def runAsFuture(input: SegmentWISDMSessionsSpec) =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.sourceDataSetId)

      // user ids
      userIds <- dsa.dataSetRepo.find(projection = Seq(FieldName.userId)).map(_.map(json => (json \ FieldName.userId).as[Int]).toSet)

      // activities
      activityField <- dsa.fieldRepo.get(FieldName.activity).map(_.get)
      activities = activityField.enumValues.map(_._1.toInt)

      // max session id
      maxSessionId <- dsa.dataSetRepo.max(FieldName.sessionId).map(_.get.as[Int])

      userActivitySessionIds = for { x <- userIds; y <- activities; z <- 1 to maxSessionId } yield (x, y, z)

      // user-id-activity-session as a source
      userActivitySessionSource: Source[(Int, Int, Int), NotUsed] = Source.fromIterator(() => userActivitySessionIds.toIterator)

      // fields
      fields <- dsa.fieldRepo.find()

      // stream of new jsons updated in a given order
      newSource: Source[JsObject, NotUsed] = {
        userActivitySessionSource.mapAsync(1) { case (userId, activity, sessionId) =>
          dsa.dataSetRepo.find(
            Seq(FieldName.userId #== userId, FieldName.activity #== activity, FieldName.sessionId #== sessionId)
          ).map { userSessionJsons =>
            logger.info(s"Processing ${userSessionJsons.size} jsons for the user '$userId' and activity $activity and session $sessionId.")

            val sortedSessionItems = userSessionJsons.map { json =>
              val timeStamp = (json \ FieldName.timeStamp).as[Long]
              (timeStamp, json)
            }.toSeq.sortBy(_._1).map(_._2)

            sortedSessionItems.sliding(input.segmentSize, input.segmentStep).toList.zipWithIndex.flatMap { case (jsons, index) =>
              if (input.allowLastShorterSegment || jsons.length == input.segmentSize) {
                jsons.map(
                  _.+(newSegmentIdField.name, JsNumber(index)).-(JsObjectIdentity.name)
                )
              } else
                Nil
            }
          }
        }.mapConcat[JsObject](identity _)
      }

      // save the updated json stream as a new (derived) data set
      _ <- dataSetService.saveDerivedDataSet(dsa, input.resultDataSetSpec, newSource, (fields ++ Seq(newSegmentIdField)).toSeq, input.streamSpec, true)
    } yield
      ()
}

case class SegmentWISDMSessionsSpec(
  sourceDataSetId: String,
  segmentSize: Int,
  segmentStep: Int,
  allowLastShorterSegment: Boolean,
  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec
)
