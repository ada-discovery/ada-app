package runnables.luxpark

import javax.inject.Inject

import persistence.RepoSynchronizer
import play.api.Configuration
import runnables.DataSetId.luxpark
import persistence.RepoTypes.DataSpaceMetaInfoRepo
import persistence.dataset.DataSetAccessorFactory
import runnables.GuiceBuilderRunnable
import services.RedCapServiceFactory._
import services.{RedCapServiceFactory, RedCapService}

import scala.concurrent.Await._
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

class ImportLuxParkDataFromRedCap @Inject() (
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    redCapServiceFactory: RedCapServiceFactory,
    configuration: Configuration
  ) extends Runnable {

  private val redCapService = defaultRedCapService(redCapServiceFactory, configuration)
  private val timeout = 120000 millis

  lazy val dataSetAccessor =
    result(dsaf.register("Lux Park", luxpark, "Clinical", Some(LuxParkDataSetSetting.Luxpark)), timeout)

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