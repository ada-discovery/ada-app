package runnables.other

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.ada.server.dataaccess.StreamSpec
import javax.inject.Inject
import org.ada.server.models.{Field, FieldTypeId}
import org.ada.server.models.DerivedDataSetSpec
import org.incal.core.InputFutureRunnable
import org.incal.core.dataaccess.Criterion._
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.{JsNumber, JsObject}
import org.ada.server.services.DataSetService
import org.ada.server.field.FieldUtil._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class AddSessionIdToWISDM @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService
) extends InputFutureRunnable[AddSessionIdToWISDMSpec] {

  private val logger = Logger

  private object FieldName {
    val timeStamp = "timestamp"
    val userId = "user"
    val activity = "activity"
  }

  private val newSessionIdField = Field("sessionId", Some("Session Id"), FieldTypeId.Integer)

  override def runAsFuture(input: AddSessionIdToWISDMSpec) = {
    val dsa = dsaf(input.sourceDataSetId).get

    for {
      // user ids
      userIds <- dsa.dataSetRepo.find(projection = Seq(FieldName.userId)).map(_.map(json => (json \ FieldName.userId).as[Int]).toSet)

      // activities
      activityField <- dsa.fieldRepo.get(FieldName.activity).map(_.get)
      activities = activityField.numValues.map(_.map(_._1.toInt)).get

      userActivities = for { x <- userIds; y <- activities } yield (x, y)

      // user id activity as a source
      userActivitySource: Source[(Int, Int), NotUsed] = Source.fromIterator(() => userActivities.toIterator)

      fields <- dsa.fieldRepo.find()

      // stream of new jsons updated in a given order
      newSource: Source[JsObject, NotUsed] = {
        userActivitySource.mapAsync(1) { case (userId, activity) =>
          dsa.dataSetRepo.find(Seq(FieldName.userId #== userId, FieldName.activity #== activity, FieldName.timeStamp #!= 0)).map { userActivityJsons =>
            logger.info(s"Processing ${userActivityJsons.size} jsons for the user '$userId' and activity $activity.")

            val sortedUserActivityItems = userActivityJsons.map { json =>
              val timeStamp = (json \ FieldName.timeStamp).as[Long]
              (timeStamp, json)
            }.toSeq.sortBy(_._1)

            val size = sortedUserActivityItems.length
            var sessionId = 1

            for (i <- 0 until size) yield {
              val (timestamp, json) = sortedUserActivityItems(i)

              if (i > 0) {
                val (prevTimestamp, _) = sortedUserActivityItems(i - 1)

                if (timestamp - prevTimestamp > input.maxDiffBetweenSessions) {
                  sessionId += 1
                }
              }
              json.+("sessionId", JsNumber(sessionId))
            }
          }
        }.mapConcat[JsObject](identity _)
      }

      // save the updated json stream as a new (derived) data set
      _ <- dataSetService.saveDerivedDataSet(dsa, input.resultDataSetSpec, newSource, (fields ++ Seq(newSessionIdField)).toSeq, input.streamSpec, true)
    } yield
      ()
  }

  override def inputType = typeOf[AddSessionIdToWISDMSpec]
}

case class AddSessionIdToWISDMSpec(
  sourceDataSetId: String,
  maxDiffBetweenSessions: Long,
  resultDataSetSpec: DerivedDataSetSpec,
  streamSpec: StreamSpec
)
