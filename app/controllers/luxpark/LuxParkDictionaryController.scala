package controllers.luxpark

import javax.inject.{Inject, Named}

import controllers.{DictionaryControllerImpl, DictionaryController, StudyRouter}
import persistence.DictionaryFieldRepo

class LuxParkDictionaryController @Inject()(
   @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryControllerImpl(dictionaryRepo) {

  override protected val dataSetName = "LuxPark"

  override protected def router = StudyRouter.LuxPark.dictionaryRouter
}