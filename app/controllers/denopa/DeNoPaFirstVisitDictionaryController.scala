package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, StudyRouter}
import persistence.DictionaryFieldRepo

class DeNoPaFirstVisitDictionaryController @Inject() (
    @Named("DeNoPaFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa First Visit"

  override protected def router = StudyRouter.DeNoPaFirstVisit.dictionaryRouter
}