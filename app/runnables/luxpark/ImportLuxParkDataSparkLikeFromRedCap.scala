package runnables.luxpark

import javax.inject.{Named, Inject}

import models.DataSetId._
import models.DataSetMetaInfo
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import runnables.GuiceBuilderRunnable
import services.RedCapService
import scala.concurrent.duration._

import scala.concurrent.Await

class ImportLuxParkDataSparkLikeFromRedCap @Inject() (
    dsaf: DataSetAccessorFactory,
    @Named("LuxDistParkRepo") dataDistRepo: JsObjectDistRepo,
    redCapService: RedCapService
  ) extends Runnable {

  private val timeout = 120000 millis

  val metaInfo = DataSetMetaInfo(None, luxpark.toString, "Lux Park")

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
    val records = Await.result(futureRecords, timeout)

    dataDistRepo.saveJson(records)
  }
}

object ImportLuxParkDataSparkLikeFromRedCap extends GuiceBuilderRunnable[ImportLuxParkDataSparkLikeFromRedCap] with App { run }