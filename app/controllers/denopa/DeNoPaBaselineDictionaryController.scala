package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, StudyRouter}
import persistence.DictionaryFieldRepo

class DeNoPaBaselineDictionaryController @Inject() (
    @Named("DeNoPaBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa Baseline"

  override protected def router = StudyRouter.DeNoPaBaseline.dictionaryRouter
}