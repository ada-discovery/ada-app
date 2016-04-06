package controllers.dataset.denopa

import javax.inject.Inject

import models.DataSetId._
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory

class DenopaCuratedSecondvisitController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DenopaController(denopa_curated_secondvisit, dsaf, dataSetMetaInfoRepo) {

  override protected val listViewColumns = Some(Seq("Line_Nr", "Probanden_Nr", "Geburtsdatum", "c_group"))

  override protected val overviewFieldNamesConfPrefix = "denopa.curatedsecondvisit"

  override protected val defaultDistributionFieldName = "c_Alter"

  override protected val defaultScatterXFieldName = "c_Alter"
  override protected val defaultScatterYFieldName = "c_AESD_I_mean"
}