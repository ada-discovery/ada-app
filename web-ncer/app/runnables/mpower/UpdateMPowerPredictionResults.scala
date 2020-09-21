package runnables.mpower

import javax.inject.Inject

import org.incal.core.runnables.FutureRunnable
import org.incal.play.GuiceRunnableApp
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global

class UpdateMPowerPredictionResults @Inject() (dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val dataSetId = "mpower_challenge.walking_activity_training_w_demographics_results"

  private val inputFieldPaths = Seq(
    "accel_walking_outboundu002ejsonu002eitems.x",
    "accel_walking_outboundu002ejsonu002eitems.y",
    "accel_walking_outboundu002ejsonu002eitems.z"
  )

  private val outputFieldPaths = Seq(
    "accel_walking_outboundu002ejsonu002eitems.y"
  )

  override def runAsFuture =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(dataSetId)
      dataSetRepo = dsa.dataSetRepo

      jsons <- dataSetRepo.find()

//      Some(json) <- dataSetRepo.get(BSONObjectID.apply("5970fd04f6000080036aec20"))

      _ <- {
        val newJsons = jsons.map ( json =>
          json.++(Json.obj(
            "inputOutputSpec-sourceDataSetId" -> "mpower_challenge.walking_activity_training_w_demographics"
          ))
        )

        dataSetRepo.update(newJsons)
      }

//      _ <- {
//        val newJson = json.+("resultDataSetId", JsString("mpower_challenge.walking_activity_training_norms_rc_weights_11"))
//
//        dataSetRepo.update(newJson)
//      }

//      _ <- dataSetRepo.delete(Seq(BSONObjectID.parse("59900e89f800008b013435a7").get, BSONObjectID.parse("59928d55f70000b701261406").get, BSONObjectID.parse("599043ccf80000210734c059").get))
    } yield
      ()
}

object UpdateMPowerPredictionResults extends GuiceRunnableApp[UpdateMPowerPredictionResults]
