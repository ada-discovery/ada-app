package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DictionaryControllerImpl
import persistence.DictionaryFieldRepo

class DeNoPaCuratedFirstVisitDictionaryController @Inject() (
    @Named("DeNoPaCuratedFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryControllerImpl(dictionaryRepo) {

  override val dataSetId = "denopa-curated-firstvisit"

  override protected val dataSetName = "DeNoPa Curated First Visit"
}