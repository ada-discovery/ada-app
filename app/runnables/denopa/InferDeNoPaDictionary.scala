package runnables.denopa

import javax.inject.{Named, Inject}

import persistence.DictionaryFieldRepo
import play.api.libs.json.Json
import services.DeNoPaSetting
import runnables.{InferDictionary, GuiceBuilderRunnable}

protected abstract class InferDeNoPaDictionary(dictionaryRepo: DictionaryFieldRepo) extends InferDictionary(dictionaryRepo) {
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> "1")
}

class InferDeNoPaBaselineDictionary @Inject()(
  @Named("DeNoPaBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo) extends InferDeNoPaDictionary(dictionaryRepo)

class InferDeNoPaFirstVisitDictionary @Inject()(
  @Named("DeNoPaFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo) extends InferDeNoPaDictionary(dictionaryRepo)

class InferDeNoPaCuratedBaselineDictionary @Inject()(
  @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo) extends InferDeNoPaDictionary(dictionaryRepo) {
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> 1)
}

class InferDeNoPaCuratedFirstVisitDictionary @Inject()(
  @Named("DeNoPaCuratedFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo) extends InferDeNoPaDictionary(dictionaryRepo) {
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> 1)
}

object InferDeNoPaBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaBaselineDictionary] with App { run }
object InferDeNoPaFirstVisitDictionary extends GuiceBuilderRunnable[InferDeNoPaFirstVisitDictionary] with App { run }
object InferDeNoPaCuratedBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaCuratedBaselineDictionary] with App { run }
object InferDeNoPaCuratedFirstVisitDictionary extends GuiceBuilderRunnable[InferDeNoPaCuratedFirstVisitDictionary] with App { run }
