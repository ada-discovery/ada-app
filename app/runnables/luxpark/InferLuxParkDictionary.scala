package runnables.luxpark

import models.DataSetId._
import runnables.{GuiceBuilderRunnable, InferDictionary}
import services.DeNoPaSetting

// TODO: Introduce a proper type inference setting for LuxPark Data
class InferLuxParkDictionary extends InferDictionary(luxpark, DeNoPaSetting.typeInferenceProvider)

object InferLuxParkDictionary extends GuiceBuilderRunnable[InferLuxParkDictionary] with App { run }