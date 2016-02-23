package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryControllerImpl, DictionaryController, StudyRouter}
import persistence.DictionaryFieldRepo

class DeNoPaFirstVisitDictionaryController @Inject() (
    @Named("DeNoPaFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryControllerImpl(dictionaryRepo) {

  override val dataSetId = "denopa-firstvisit"

  override protected val dataSetName = "DeNoPa First Visit"

  override protected def router = StudyRouter.DeNoPaFirstVisit.dictionaryRouter
}