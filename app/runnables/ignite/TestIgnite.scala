package runnables.ignite

import com.google.inject.Inject
import dataaccess.ignite.CacheAsyncCrudRepoFactory
import models.DataSetFormattersAndIds.{DataSetSettingIdentity, serializableBSONObjectIDFormat, serializableDataSetSettingFormat}
import models.DataSetSetting
import reactivemongo.bson.BSONObjectID
import org.incal.play.GuiceRunnableApp
import org.incal.core.dataaccess.Criterion.Infix

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class TestIgnite @Inject() (cacheRepoFactory: CacheAsyncCrudRepoFactory) extends Runnable {

  implicit val objectId = serializableBSONObjectIDFormat
  val repo = cacheRepoFactory.applyMongo[BSONObjectID, DataSetSetting]("dataset_settings", Some("dataset_settingsx"))

  override def run = {
    println(getAll.map(x => s"${x._id.toString} ${x.dataSetId}, ${x.exportOrderByFieldName}").mkString("\n"))

    val updateFuture =
      repo.get(BSONObjectID.parse("577e09dc8e0000d00093fc06").get).flatMap{ setting =>
        val updatedSetting = setting.get.copy(exportOrderByFieldName = Some("lala"))
        repo.update(updatedSetting)
      }


    Await.result(updateFuture, 2 minutes)

    val saveFuture =
      repo.get(BSONObjectID.parse("577e09dc8e0000d00093fc06").get).flatMap{ setting =>
        val newSetting = setting.get.copy(exportOrderByFieldName = Some("lili"), dataSetId = "Testxx", _id = None)
        repo.save(newSetting)
      }

    Await.result(saveFuture, 2 minutes)

    println(getAll.map(x => s"${x._id.toString} ${x.dataSetId}, ${x.exportOrderByFieldName}").mkString("\n"))
  }

  private def getAll = {
    val allFuture = repo.find()
    Await.result(allFuture, 2 minutes)
  }

  private def filter = {
    val allFuture = repo.find(
      criteria = Seq("keyFieldName" #== "sampleid"),
      projection = Seq(
        "dataSetId",
        "keyFieldName",
        "exportOrderByFieldName",
        "overviewChartElementGridWidth",
        "defaultScatterXFieldName",
        "defaultScatterYFieldName",
        "defaultDistributionFieldName"
      )
    )
    Await.result(allFuture, 2 minutes)
  }
}

object TestIgnite extends GuiceRunnableApp[TestIgnite]
