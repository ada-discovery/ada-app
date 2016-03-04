package runnables.luxpark

import javax.inject.Inject

import models.DataSetMetaInfo
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

  val metaInfo = DataSetMetaInfo(None, luxpark, "Lux Park")

  val dataSetAccessor = {
    val futureAccessor = dsaf.register(metaInfo)
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