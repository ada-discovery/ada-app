package runnables.luxpark

import javax.inject.{Named, Inject}

import play.api.Configuration
import runnables.DataSetId.lux_park_clinical
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import runnables.GuiceBuilderRunnable
import services.RedCapServiceFactory._
import services.{RedCapServiceFactory, RedCapService}
import scala.concurrent.Await._
import scala.concurrent.duration._

import scala.concurrent.Await

class ImportLuxParkDataSparkLikeFromRedCap @Inject() (
    dsaf: DataSetAccessorFactory,
//    @Named("LuxDistParkRepo") dataDistRepo: JsObjectDistRepo,
    redCapServiceFactory: RedCapServiceFactory,
    configuration: Configuration
  ) extends Runnable {

  private val redCapService = defaultRedCapService(redCapServiceFactory, configuration)
  private val timeout = 120000 millis

  lazy val dataSetAccessor =
    result(dsaf.register("Lux Park", lux_park_clinical, "Lux Park", Some(LuxParkDataSetSettings.Clinical)), timeout)

  private val syncDataRepo = RepoSynchronizer(dataSetAccessor.dataSetRepo, timeout)

  override def run = {
    // delete all the records
    syncDataRepo.deleteAll

    // insert the records obtained from the RedCap service to the repo (db) one by one
    val futureRecords = redCapService.listRecords("cdisc_dm_usubjd", "")
    val records = Await.result(futureRecords, timeout)

//    dataDistRepo.saveJson(records)
  }
}

object ImportLuxParkDataSparkLikeFromRedCap extends GuiceBuilderRunnable[ImportLuxParkDataSparkLikeFromRedCap] with App { run }