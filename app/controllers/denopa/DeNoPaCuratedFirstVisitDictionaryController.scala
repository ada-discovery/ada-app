package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, StudyRouter}
import persistence.DictionaryFieldRepo

class DeNoPaCuratedFirstVisitDictionaryController @Inject() (
    @Named("DeNoPaCuratedFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa Curated First Visit"

  override protected def router = StudyRouter.DeNoPaCuratedFirstVisit.dictionaryRouter
}