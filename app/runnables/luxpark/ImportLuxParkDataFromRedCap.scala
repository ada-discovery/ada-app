package runnables.luxpark

import javax.inject.Inject

import models.{DataSetSetting, DataSetMetaInfo}
import persistence.RepoSynchronizer
import models.DataSetId._
import persistence.dataset.DataSetAccessorFactory
import runnables.GuiceBuilderRunnable
import services.RedCapService

import scala.concurrent.Await
import scala.concurrent.duration._

class ImportLuxParkDataFromRedCap @Inject() (
    dsaf: DataSetAccessorFactory,
    redCapService: RedCapService
  ) extends Runnable {

  private val timeout = 120000 millis

  val metaInfo = DataSetMetaInfo(None, luxpark, "Lux Park", None)
  val setting = DataSetSetting(
    None,
    luxpark,
    "cdisc_dm_usubjd",
    "cdisc_dm_usubjd",
    Seq("cdisc_dm_usubjd", "redcap_event_name", "cdisc_dm_subjid_2", "dm_site", "cdisc_dm_brthdtc", "cdisc_dm_sex", "cdisc_sc_sctestcd_maritstat"),
    Seq("cdisc_dm_sex", "control_q1", "cdisc_sc_sctestcd_maritstat", "sv_age"),
    "digitsf_score",
    "bentons_score",
    "digitsf_score" ,
    Some("redcap_event_name"),
    Map(("\r", " "), ("\n", " "))
  )

  val dataSetAccessor = {
    val futureAccessor = dsaf.register(metaInfo, Some(setting))
    Await.result(futureAccessor, timeout)
  }
  private val syncDataRepo = RepoSynchronizer(dataSetAccessor.dataSetRepo, timeout)

  override def run = {
    // delete all the records
    syncDataRepo.deleteAll

    // insert the records obtained from the RedCap service to the repo (db) one by one
    val futureRecords = redCapService.listRecords("cdisc_dm_usubjd", "")
    Await.result(futureRecords, timeout).foreach(syncDataRepo.save)
  }
}

object ImportLuxParkDataFromRedCap extends GuiceBuilderRunnable[ImportLuxParkDataFromRedCap] with App { run }