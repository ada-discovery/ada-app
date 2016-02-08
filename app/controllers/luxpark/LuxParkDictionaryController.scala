package controllers.luxpark

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, DictionaryRouter}
import persistence.DictionaryFieldRepo

class LuxParkDictionaryController @Inject()(
   @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "LuxPark"

  override protected def router = DictionaryRouter(
    routes.LuxParkDictionaryController.overviewList,
    routes.LuxParkDictionaryController.overviewList(),
    routes.LuxParkDictionaryController.get,
    routes.LuxParkDictionaryController.save,
    routes.LuxParkDictionaryController.update
  )
}