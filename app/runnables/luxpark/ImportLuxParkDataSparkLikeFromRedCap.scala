package runnables.luxpark

import javax.inject.{Named, Inject}

import persistence.RepoSynchronizer
import persistence.RepoTypeRegistry._
import runnables.GuiceBuilderRunnable
import services.RedCapService
import scala.concurrent.duration._

import scala.concurrent.Await

class ImportLuxParkDataSparkLikeFromRedCap @Inject() (
    @Named("LuxParkRepo") dataRepo: JsObjectCrudRepo,
    @Named("LuxDistParkRepo") dataDistRepo: JsObjectDistRepo,
    redCapService: RedCapService
  ) extends Runnable {

  private val timeout = 120000 millis
  private val syncDataRepo = RepoSynchronizer(dataRepo, timeout)


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