package controllers.dataset.denopa

import javax.inject.Inject

import controllers.dataset.DataSetControllerImpl
import models.DataSetId._
import models.DataSetSetting
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory

class DenopaSecondvisitController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DataSetControllerImpl(denopa_secondvisit, dsaf, dataSetMetaInfoRepo) {

  override protected def setting = DataSetSetting(
    None,
    None,
    "Probanden_Nr",
    "cdisc_dm_usubjd",
    Seq("Probanden_Nr", "Geburtsdatum", "c_group"),
    Seq("Geschlecht", "c_group", "c_Alter"),
    "c_Alter",
    "c_AESD_I_mean",
    "c_Alter",
    None,
    List(("\r", " "), ("\n", " "))
  )
}