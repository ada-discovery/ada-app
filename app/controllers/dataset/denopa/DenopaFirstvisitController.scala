package controllers.dataset.denopa

import javax.inject.Inject
import controllers.dataset.DataSetControllerImpl
import models.DataSetId._
import models.DataSetSetting
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory

class DenopaFirstvisitController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DataSetControllerImpl(denopa_firstvisit, dsaf, dataSetMetaInfoRepo) {

  override protected def setting = DataSetSetting(
    None,
    None,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "b_Gruppe"),
    Seq("Geschlecht", "b_Gruppe", "a_Alter"),
    "a_Alter",
    "b_AESD_I_mean",
    "a_Alter",
    None,
    List(("\r", " "), ("\n", " "))
  )
}