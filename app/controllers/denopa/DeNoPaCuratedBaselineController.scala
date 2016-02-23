package controllers.denopa

import javax.inject.{Inject, Named}

import persistence.DictionaryFieldRepo

class DeNoPaCuratedBaselineController @Inject() (
    @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override val dataSetId = "denopa-curated-baseline"

  override protected val dataSetName = "DeNoPa Curated Baseline"

  override protected val listViewColumns = Some(Seq("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.curatedbaseline"

  override protected val defaultScatterYFieldName = "a_AESD_I_mean"
}