package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryControllerImpl, DictionaryController, StudyRouter}
import persistence.DictionaryFieldRepo

class DeNoPaCuratedBaselineDictionaryController @Inject() (
    @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DictionaryControllerImpl(dictionaryRepo) {

  override val dataSetId = "denopa-curated-baseline"

  override protected val dataSetName = "DeNoPa Curated Baseline"

  override protected def router = StudyRouter.DeNoPaCuratedBaseline.dictionaryRouter
}