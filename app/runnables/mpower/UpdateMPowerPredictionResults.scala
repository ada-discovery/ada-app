package runnables

import javax.inject.Inject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsArray, JsString, Json}
import reactivemongo.bson.BSONObjectID

class UpdateMPowerPredictionResults @Inject() (dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val dataSetId = "mpower_challenge.walking_activity_training_results"
  private val dataSetRepo = dsaf(dataSetId).get.dataSetRepo

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
//      jsons <- dataSetRepo.find()

//      Some(json) <- dataSetRepo.get(BSONObjectID.apply("5970fd04f6000080036aec20"))

//      _ <- {
//        val newJsons = jsons.map ( json =>
//          json.++(Json.obj(
//            "inputSeriesFieldPaths" -> JsArray(inputFieldPaths.map(JsString(_))),
//            "outputSeriesFieldPaths" -> JsArray(outputFieldPaths.map(JsString(_)))
//          ))
//        )
//
//        dataSetRepo.update(newJsons)
//      }

//      _ <- {
//        val newJson = json.+("resultDataSetId", JsString("mpower_challenge.walking_activity_training_norms_rc_weights_11"))
//
//        dataSetRepo.update(newJson)
//      }

      _ <- dataSetRepo.delete(Seq(BSONObjectID.apply("598b35e0f6000088014065e9"), BSONObjectID.apply("598b69faf600006b0440edff")))
    } yield
      ()
}

object UpdateMPowerPredictionResults extends GuiceBuilderRunnable[UpdateMPowerPredictionResults] with App { run }
