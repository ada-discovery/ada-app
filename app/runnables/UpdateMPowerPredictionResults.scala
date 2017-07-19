package runnables

import javax.inject.Inject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsArray, JsString, Json}
import reactivemongo.bson.BSONObjectID

class UpdateMPowerPredictionResults @Inject() (dsaf: DataSetAccessorFactory) extends Runnable {

  private val dataSetId = "mpower_challenge.walking_activity_training_results"
  private val dataSetRepo = dsaf(dataSetId).get.dataSetRepo

  private val timeout = 120000 millis

  private val inputFieldPaths = Seq(
    "accel_walking_outboundu002ejsonu002eitems.x",
    "accel_walking_outboundu002ejsonu002eitems.y",
    "accel_walking_outboundu002ejsonu002eitems.z"
  )

  private val outputFieldPaths = Seq(
    "accel_walking_outboundu002ejsonu002eitems.y"
  )

  override def run = {
    val future = for {
//      jsons <- dataSetRepo.find()

      Some(json) <- dataSetRepo.get(BSONObjectID.apply("596aceaef500003403f278d8"))

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


      _ <- {
        val newJson = json.+("resultDataSetName", JsString("Walking Activity Training (RC) Weights [26]"))

        dataSetRepo.update(newJson)
      }

      _ <- dataSetRepo.delete(Seq(BSONObjectID.apply("596a16fef700001803fbe1f4"), BSONObjectID.apply("596a1858f70000fe02fc6918")))
    } yield
      ()

    Await.result(future, timeout)
  }
}

object UpdateMPowerPredictionResults extends GuiceBuilderRunnable[UpdateMPowerPredictionResults] with App { run }
