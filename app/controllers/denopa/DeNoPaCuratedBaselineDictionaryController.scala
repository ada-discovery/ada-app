package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, DictionaryRouter}
import persistence.DictionaryFieldRepo

class DeNoPaCuratedBaselineDictionaryController @Inject() (
    @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa Curated Baseline"

  override protected def router = DictionaryRouter(
    routes.DeNoPaCuratedBaselineDictionaryController.overviewList,
    routes.DeNoPaCuratedBaselineDictionaryController.overviewList(),
    routes.DeNoPaCuratedBaselineDictionaryController.get,
    routes.DeNoPaCuratedBaselineDictionaryController.save,
    routes.DeNoPaCuratedBaselineDictionaryController.update
  )
}