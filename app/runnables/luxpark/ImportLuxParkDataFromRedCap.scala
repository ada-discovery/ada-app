package runnables.luxpark

import javax.inject.{Inject, Named}

import persistence.RepoSynchronizer
import persistence.RepoTypeRegistry._
import runnables.GuiceBuilderRunnable
import services.RedCapService

import scala.concurrent.Await
import scala.concurrent.duration._

class ImportLuxParkDataFromRedCap @Inject() (
    @Named("LuxParkRepo") dataRepo: JsObjectCrudRepo,
    redCapService: RedCapService
  ) extends Runnable {

  private val timeout = 120000 millis
  private val syncDataRepo = RepoSynchronizer(dataRepo, timeout)

  override def run = {
    // delete all the records
    syncDataRepo.deleteAll

    // insert the records obtained from the RedCap service to the repo (db) one by one
    val futureRecords = redCapService.listRecords("cdisc_dm_usubjd", "")
    Await.result(futureRecords, timeout).foreach(syncDataRepo.save(_))
  }
}

object ImportLuxParkDataFromRedCap extends GuiceBuilderRunnable[ImportLuxParkDataFromRedCap] with App { run }