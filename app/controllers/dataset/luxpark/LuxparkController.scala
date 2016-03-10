package controllers.dataset.luxpark

import javax.inject.Inject

import controllers.dataset.DataSetControllerImpl
import models.DataSetId._
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import services.RedCapService

class LuxparkController @Inject()(
    dsaf: DataSetAccessorFactory,
    redCapService: RedCapService,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DataSetControllerImpl(luxpark, dsaf, dataSetMetaInfoRepo) {

  override protected val keyField = "cdisc_dm_usubjd"

  override protected val exportOrderByField = "cdisc_dm_usubjd"

  override protected val listViewColumns = Some(Seq("cdisc_dm_usubjd", "redcap_event_name", "cdisc_dm_subjid_2", "dm_site", "cdisc_dm_brthdtc", "cdisc_dm_sex", "cdisc_sc_sctestcd_maritstat"))

  override protected val overviewFieldNamesConfPrefix = "luxpark"

  override protected val defaultScatterXFieldName = "digitsf_score"

  override protected val defaultScatterYFieldName = "bentons_score"

  override protected val defaultDistributionFieldName = "digitsf_score"

  override protected val tranSMARTVisitField = Some("redcap_event_name")

  override protected val tranSMARTReplacements = List(("\r", " "), ("\n", " "))
}