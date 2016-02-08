package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, DictionaryRouter}
import persistence.DictionaryFieldRepo

class DeNoPaFirstVisitDictionaryController @Inject() (
    @Named("DeNoPaFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa First Visit"

  override protected def router = DictionaryRouter(
    routes.DeNoPaFirstVisitDictionaryController.overviewList,
    routes.DeNoPaFirstVisitDictionaryController.overviewList(),
    routes.DeNoPaFirstVisitDictionaryController.get,
    routes.DeNoPaFirstVisitDictionaryController.save,
    routes.DeNoPaFirstVisitDictionaryController.update
  )
}