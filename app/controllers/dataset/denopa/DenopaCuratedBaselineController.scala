package controllers.dataset.denopa

import javax.inject.Inject
import models.DataSetId._
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory

class DenopaCuratedBaselineController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DenopaController (denopa_curated_baseline, dsaf, dataSetMetaInfoRepo) {

  override protected val listViewColumns = Some(Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.curatedbaseline"

  override protected val defaultScatterYFieldName = "a_AESD_I_mean"
}