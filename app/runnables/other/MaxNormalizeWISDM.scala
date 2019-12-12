package runnables.other

import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import org.incal.core.dataaccess.StreamSpec
import javax.inject.Inject
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsNull, JsNumber, JsObject, Json}
import org.ada.server.services.DataSetService
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.ada.server.models.datatrans.ResultDataSetSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MaxNormalizeWISDM @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService)(
  implicit materializer: Materializer
) extends InputFutureRunnableExt[MaxNormalizeWISDMSpec] {

  private object FieldName {
    val xAcceleration = "x_accel"
    val yAcceleration = "y_accel"
    val zAcceleration = "z_accel"
    val timeStamp = "timestamp"
    val userId = "user"
    val activity = "activity"
  }

  private val flatFlow = Flow[Option[JsObject]].collect{ case Some(a) => a }

  override def runAsFuture(input: MaxNormalizeWISDMSpec) = {
    val dsa = dsaf(input.sourceDataSetId).get

    for {
      // x, y, and z maxes
      xAccelMax <- dsa.dataSetRepo.max(FieldName.xAcceleration).map(_.get.as[Double])
      yAccelMax <- dsa.dataSetRepo.max(FieldName.yAcceleration).map(_.get.as[Double])
      zAccelMax <- dsa.dataSetRepo.max(FieldName.zAcceleration).map(_.get.as[Double])

      // fields
      fields <- dsa.fieldRepo.find()

      // input stream
      inputStream <- dsa.dataSetRepo.findAsStream()

      // altered stream
      alteredStream = inputStream.map { json =>
        val xAccel = asDouble(json, FieldName.xAcceleration)
        val yAccel = asDouble(json, FieldName.yAcceleration)
        val zAccel = asDouble(json, FieldName.zAcceleration)

        // save only those with defined x, y, and z acceleration
        if (xAccel.isDefined && yAccel.isDefined && zAccel.isDefined) {
          val newJson = json.++(Json.obj(
            FieldName.xAcceleration -> xAccel.get / xAccelMax,
            FieldName.yAcceleration -> yAccel.get / yAccelMax,
            FieldName.zAcceleration -> zAccel.get / zAccelMax
          ))

          Some(newJson)
        } else
          None
      }

      // save the updated json stream as a new (derived) data set
      _ <- dataSetService.saveDerivedDataSet(dsa, input.resultDataSetSpec, alteredStream.via(flatFlow), fields.toSeq, input.streamSpec, true)
    } yield
      ()
  }

  private def asDouble(json: JsObject, fieldName: String) =
    (json \ fieldName).toOption.flatMap(jsValue =>
      jsValue match {
        case JsNull => None
        case _ => Some(jsValue.as[Double])
      }
    )
}

case class MaxNormalizeWISDMSpec(
  sourceDataSetId: String,
  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec
)
