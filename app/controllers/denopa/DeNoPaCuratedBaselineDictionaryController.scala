package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryController, StudyRouter}
import persistence.DictionaryFieldRepo

class DeNoPaCuratedBaselineDictionaryController @Inject() (
    @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa Curated Baseline"

  override protected def router = StudyRouter.DeNoPaCuratedBaseline.dictionaryRouter
}