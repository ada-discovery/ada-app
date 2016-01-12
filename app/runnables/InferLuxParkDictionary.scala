package runnables

import javax.inject.{Named, Inject}

import persistence.DictionaryFieldRepo
import play.api.libs.json.Json
import services.DeNoPaSetting

class InferLuxParkDictionary @Inject()(
  @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo) extends InferDictionary(dictionaryRepo) {

  // TODO: Introduce a proper type inference setting for LuxPark Data
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("cdisc_dm_usubjd" -> "ND0001")
}

object InferLuxParkDictionary extends GuiceBuilderRunnable[InferLuxParkDictionary] with App { run }