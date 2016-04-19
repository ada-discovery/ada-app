package runnables.luxpark

import models.DataSetId._
import runnables.GuiceBuilderRunnable
import services.{DataSetService, DeNoPaSetting}
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.Await.result

class InferLuxParkDictionary @Inject() (dataSetService: DataSetService) extends Runnable {

  override def run =
    result(
      dataSetService.inferDictionary(
        luxpark,
        // TODO: Introduce a proper type inference setting for LuxPark Data
        DeNoPaSetting.typeInferenceProvider
      ),
      120000 millis
    )
}

object InferLuxParkDictionary extends GuiceBuilderRunnable[InferLuxParkDictionary] with App { run }