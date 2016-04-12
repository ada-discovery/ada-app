package controllers.dataset.luxpark

import javax.inject.Inject

import controllers.dataset.DataSetControllerImpl
import models.DataSetId._
import models.DataSetSetting
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import services.RedCapService

class LuxparkController @Inject()(
    dsaf: DataSetAccessorFactory,
    redCapService: RedCapService,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DataSetControllerImpl(luxpark, dsaf, dataSetMetaInfoRepo) {

  override protected def setting = DataSetSetting(
    None,
    None,
    "cdisc_dm_usubjd",
    "cdisc_dm_usubjd",
    Seq("cdisc_dm_usubjd", "redcap_event_name", "cdisc_dm_subjid_2", "dm_site", "cdisc_dm_brthdtc", "cdisc_dm_sex", "cdisc_sc_sctestcd_maritstat"),
    Seq("cdisc_dm_sex", "control_q1", "cdisc_sc_sctestcd_maritstat", "sv_age"),
    "digitsf_score",
    "bentons_score",
    "digitsf_score" ,
    Some("redcap_event_name"),
    List(("\r", " "), ("\n", " "))
  )
}