package runnables.mpower

import javax.inject.Inject

import persistence.dataset.DataSetAccessorFactory
import reactivemongo.bson.BSONObjectID
import runnables.{FutureRunnable, GuiceBuilderRunnable}

import scala.concurrent.ExecutionContext.Implicits.global

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

      _ <- dataSetRepo.delete(Seq(BSONObjectID("59900e89f800008b013435a7"), BSONObjectID("59928d55f70000b701261406"), BSONObjectID("599043ccf80000210734c059")))
    } yield
      ()
}

object UpdateMPowerPredictionResults extends GuiceBuilderRunnable[UpdateMPowerPredictionResults] with App { run }
