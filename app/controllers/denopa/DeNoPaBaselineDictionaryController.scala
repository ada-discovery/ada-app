package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, DictionaryRouter}
import persistence.DictionaryFieldRepo

class DeNoPaBaselineDictionaryController @Inject() (
    @Named("DeNoPaBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa Baseline"

  override protected def router = DictionaryRouter(
    routes.DeNoPaBaselineDictionaryController.overviewList,
    routes.DeNoPaBaselineDictionaryController.overviewList(),
    routes.DeNoPaBaselineDictionaryController.get,
    routes.DeNoPaBaselineDictionaryController.save,
    routes.DeNoPaBaselineDictionaryController.update
  )
}