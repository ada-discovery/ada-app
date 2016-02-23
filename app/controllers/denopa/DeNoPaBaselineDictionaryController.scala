package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DictionaryControllerImpl
import persistence.DictionaryFieldRepo

class DeNoPaBaselineDictionaryController @Inject() (
    @Named("DeNoPaBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryControllerImpl(dictionaryRepo) {

  override val dataSetId = "denopa-baseline"

  override protected val dataSetName = "DeNoPa Baseline"
}