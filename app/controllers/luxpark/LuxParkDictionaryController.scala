package controllers.luxpark

import javax.inject.{Inject, Named}

import controllers.DictionaryControllerImpl
import persistence.DictionaryFieldRepo

class LuxParkDictionaryController @Inject()(
   @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryControllerImpl(dictionaryRepo) {

  override val dataSetId = "luxpark"

  override protected val dataSetName = "LuxPark"
}