package runnables.other

import dataaccess.StreamSpec
import javax.inject.Inject
import models.ml.DerivedDataSetSpec
import org.incal.core.InputFutureRunnable
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsNumber, JsObject, Json}
import services.DataSetService
import dataaccess.JsonReadonlyRepoExtra._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class MaxNormalizeWISDM @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService
) extends InputFutureRunnable[MaxNormalizeWISDMSpec] {

  private object FieldName {
    val xAcceleration = "x_accel"
    val yAcceleration = "y_accel"
    val zAcceleration = "z_accel"
    val timeStamp = "timestamp"
    val userId = "user"
    val activity = "activity"
  }

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
        val xAccel = (json \ FieldName.xAcceleration).as[Double]
        val yAccel = (json \ FieldName.yAcceleration).as[Double]
        val zAccel = (json \ FieldName.zAcceleration).as[Double]

        json.++(Json.obj(
          FieldName.xAcceleration -> xAccel / xAccelMax,
          FieldName.yAcceleration -> yAccel / yAccelMax,
          FieldName.zAcceleration -> zAccel / zAccelMax
        ))
      }

      // save the updated json stream as a new (derived) data set
      _ <- dataSetService.saveDerivedDataSet(dsa, input.resultDataSetSpec, alteredStream, fields.toSeq, input.streamSpec, true)
    } yield
      ()
  }

  override def inputType = typeOf[MaxNormalizeWISDMSpec]
}

case class MaxNormalizeWISDMSpec(
  sourceDataSetId: String,
  resultDataSetSpec: DerivedDataSetSpec,
  streamSpec: StreamSpec
)
