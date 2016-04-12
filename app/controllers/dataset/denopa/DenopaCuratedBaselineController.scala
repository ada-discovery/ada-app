package controllers.dataset.denopa

import javax.inject.Inject
import controllers.dataset.DataSetControllerImpl
import models.DataSetId._
import models.DataSetSetting
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory

class DenopaCuratedBaselineController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DataSetControllerImpl(denopa_curated_baseline, dsaf, dataSetMetaInfoRepo) {

  override protected def setting = DataSetSetting(
    None,
    None,
    "Probanden_Nr",
    "Line_Nr",
    Seq("Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"),
    Seq("Geschlecht", "a_Gruppe", "b_Gruppe", "a_Alter"),
    "a_Alter",
    "a_AESD_I_mean",
    "a_Alter",
    None,
    List(("\r", " "), ("\n", " "))
  )
}