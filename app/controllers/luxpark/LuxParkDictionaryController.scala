package controllers.luxpark

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, StudyRouter}
import persistence.DictionaryFieldRepo

class LuxParkDictionaryController @Inject()(
   @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "LuxPark"

  override protected def router = StudyRouter.LuxPark.dictionaryRouter
}