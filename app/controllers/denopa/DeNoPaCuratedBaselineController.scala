package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.StudyRouter
import persistence.DictionaryFieldRepo

class DeNoPaCuratedBaselineController @Inject() (
    @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override val dataSetName = "DeNoPa Curated Baseline"

  override protected val listViewColumns = Some(Seq("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.curatedbaseline"

  override protected def router = StudyRouter.DeNoPaCuratedBaseline.dataSetRouter
}