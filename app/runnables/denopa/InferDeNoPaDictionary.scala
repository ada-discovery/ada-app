package runnables.denopa

import play.api.libs.json.Json
import services.DeNoPaSetting
import models.DataSetId._
import runnables.{InferDictionary, GuiceBuilderRunnable}

protected abstract class InferDeNoPaDictionary(dataSetId: String) extends InferDictionary(dataSetId) {
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> "1")
}

class InferDeNoPaBaselineDictionary extends InferDeNoPaDictionary(denopa_baseline)
class InferDeNoPaFirstVisitDictionary extends InferDeNoPaDictionary(denopa_firstvisit)
class InferDeNoPaCuratedBaselineDictionary extends InferDeNoPaDictionary(denopa_curated_baseline) {
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> 1)
}
class InferDeNoPaCuratedFirstVisitDictionary extends InferDeNoPaDictionary(denopa_curated_firstvisit) {
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> 1)
}

// app main launchers
object InferDeNoPaBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaBaselineDictionary] with App { run }
object InferDeNoPaFirstVisitDictionary extends GuiceBuilderRunnable[InferDeNoPaFirstVisitDictionary] with App { run }
object InferDeNoPaCuratedBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaCuratedBaselineDictionary] with App { run }
object InferDeNoPaCuratedFirstVisitDictionary extends GuiceBuilderRunnable[InferDeNoPaCuratedFirstVisitDictionary] with App { run }
