package runnables

import com.google.inject.Inject
import dataaccess.Criterion.CriterionInfix
import dataaccess.ignite.CacheAsyncCrudRepoFactory
import dataaccess.{SerializableFormat, DataSetSetting, DataSetFormattersAndIds}
import dataaccess.DataSetFormattersAndIds.DataSetSettingIdentity
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import DataSetFormattersAndIds.{serializableDataSetSettingFormat, serializableBSONObjectIDFormat}
import scala.concurrent.duration._

import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global

class TestIgnite @Inject() (cacheRepoFactory: CacheAsyncCrudRepoFactory) extends Runnable {

  implicit val objectId = serializableBSONObjectIDFormat
  val repo = cacheRepoFactory.applyMongo[BSONObjectID, DataSetSetting]("dataset_settings", Some("dataset_settingsx"))

  override def run = {
    println(getAll.map(x => s"${x._id.toString} ${x.dataSetId}, ${x.overviewChartElementGridWidth}").mkString("\n"))

    val updateFuture =
      repo.get(BSONObjectID("577e09dc8e0000d00093fc06")).flatMap{ setting =>
        val updatedSetting = setting.get.copy(overviewChartElementGridWidth = 5)
        repo.update(updatedSetting)
      }


    Await.result(updateFuture, 2 minutes)

    val saveFuture =
      repo.get(BSONObjectID("577e09dc8e0000d00093fc06")).flatMap{ setting =>
        val newSetting = setting.get.copy(overviewChartElementGridWidth = 66, dataSetId = "Testxx", _id = None)
        repo.save(newSetting)
      }

    Await.result(saveFuture, 2 minutes)

    println(getAll.map(x => s"${x._id.toString} ${x.dataSetId}, ${x.overviewChartElementGridWidth}").mkString("\n"))
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

object TestIgnite extends GuiceBuilderRunnable[TestIgnite] with App { run }
