package runnables

import javax.inject.{Named, Inject}

import persistence.RepoSynchronizer
import persistence.RepoTypeRegistry._
import services.RedCapService
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Await

class ImportLuxParkDataFromRedCap @Inject() (
    @Named("LuxParkRepo") dataRepo: JsObjectCrudRepo,
    redCapService: RedCapService
  ) extends Runnable {

  private val timeout = 120000 millis
  private val syncDataRepo = RepoSynchronizer(dataRepo, timeout)

  override def run = {
    // remove the records from the collection
    syncDataRepo.deleteAll

    // insert all the records are obtained from the service one by one
    val futureRecords = redCapService.listRecords("cdisc_dm_usubjd", "")
    Await.result(futureRecords, timeout).foreach(syncDataRepo.save(_))
  }
}

object ImportLuxParkDataFromRedCap extends GuiceBuilderRunnable[ImportLuxParkDataFromRedCap] with App {
  run
}
