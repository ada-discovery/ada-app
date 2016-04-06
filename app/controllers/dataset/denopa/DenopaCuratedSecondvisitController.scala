package controllers.dataset.denopa

import javax.inject.Inject

import models.DataSetId._
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory

class DenopaCuratedSecondvisitController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DenopaController(denopa_curated_secondvisit, dsaf, dataSetMetaInfoRepo) {

  override protected val listViewColumns = Some(Seq("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.curatedsecondvisit"

  override protected val defaultScatterYFieldName = "b_AESD_I_mean"
}