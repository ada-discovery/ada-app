package runnables.luxpark

import javax.inject.{Named, Inject}

import models.DataSetId._
import models.{DataSetSetting, DataSetMetaInfo}
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import runnables.GuiceBuilderRunnable
import services.RedCapService
import scala.concurrent.Await._
import scala.concurrent.duration._

import scala.concurrent.Await

class ImportLuxParkDataSparkLikeFromRedCap @Inject() (
    dsaf: DataSetAccessorFactory,
    @Named("LuxDistParkRepo") dataDistRepo: JsObjectDistRepo,
    redCapService: RedCapService
  ) extends Runnable {

  private val timeout = 120000 millis

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

  lazy val dataSetAccessor =
    result(dsaf.register("Lux Park", luxpark, "Lux Park", Some(setting)), timeout)

  private val syncDataRepo = RepoSynchronizer(dataSetAccessor.dataSetRepo, timeout)

  override def run = {
    // delete all the records
    syncDataRepo.deleteAll

    // insert the records obtained from the RedCap service to the repo (db) one by one
    val futureRecords = redCapService.listRecords("cdisc_dm_usubjd", "")
    val records = Await.result(futureRecords, timeout)

    dataDistRepo.saveJson(records)
  }
}

object ImportLuxParkDataSparkLikeFromRedCap extends GuiceBuilderRunnable[ImportLuxParkDataSparkLikeFromRedCap] with App { run }