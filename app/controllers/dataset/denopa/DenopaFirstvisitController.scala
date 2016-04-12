package controllers.dataset.denopa

import javax.inject.Inject
import models.DataSetId._
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory

class DenopaFirstvisitController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DenopaController(denopa_firstvisit, dsaf, dataSetMetaInfoRepo) {

  override protected val listViewColumns = Some(Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.firstvisit"

  override protected val defaultScatterYFieldName = "b_AESD_I_mean"
}