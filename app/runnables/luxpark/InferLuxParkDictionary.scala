package runnables.luxpark

import models.DataSetId._
import play.api.libs.json.Json
import runnables.{GuiceBuilderRunnable, InferDictionary}
import services.DeNoPaSetting

class InferLuxParkDictionary extends InferDictionary(luxpark) {

  // TODO: Introduce a proper type inference setting for LuxPark Data
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("cdisc_dm_usubjd" -> "ND0001")
}

object InferLuxParkDictionary extends GuiceBuilderRunnable[InferLuxParkDictionary] with App { run }