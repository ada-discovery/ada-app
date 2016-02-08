package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, DictionaryRouter}
import persistence.DictionaryFieldRepo

class DeNoPaCuratedFirstVisitDictionaryController @Inject() (
    @Named("DeNoPaCuratedFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa Curated First Visit"

  override protected def router = DictionaryRouter(
    routes.DeNoPaCuratedFirstVisitDictionaryController.overviewList,
    routes.DeNoPaCuratedFirstVisitDictionaryController.overviewList(),
    routes.DeNoPaCuratedFirstVisitDictionaryController.get,
    routes.DeNoPaCuratedFirstVisitDictionaryController.save,
    routes.DeNoPaCuratedFirstVisitDictionaryController.update
  )
}