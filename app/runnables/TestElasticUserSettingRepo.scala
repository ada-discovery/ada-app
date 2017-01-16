package runnables

import com.google.inject.Inject
import dataaccess.Criterion.Infix
import models.workspace.{UserGroup, Workspace}
import persistence.RepoTypes._
import scala.concurrent.duration._

import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global

class TestElasticUserSettingRepo @Inject()(userSettingRepo: UserSettingsRepo) extends Runnable {

//  val repo = cacheRepoFactory.applyMongo[BSONObjectID, DataSetSetting]("dataset_settings", Some("dataset_settingsx"))

  override def run = {
    val future =
      for {
        _ <- userSettingRepo.save(Workspace(None, "peter", UserGroup(None, "myGroup", Some("This is my group")), Nil, Nil))
        _ <- userSettingRepo.save(Workspace(None, "olaf", UserGroup(None, "LCSB", Some("LCSB Uni group")), Nil, Nil))
        searchResult <- userSettingRepo.find(
          criteria = Seq("userId" #== "olaf"),
          projection = Seq("_id", "userId", "collaborators._id")
        )
        all <- userSettingRepo.find()
      } yield {
        val olaf = searchResult.head
        println("Olaf")
        println(s"${olaf._id.get.stringify}, ${olaf.userId}, ${olaf.collaborators.groupName}")
        println("All:")
        println(all.map(userSetting => s"${userSetting._id.get.stringify}, ${userSetting.userId}, ${userSetting.collaborators.groupName}").mkString("\n"))
      }
    Await.result(future, 100 seconds)
  }
}

object TestElasticUserSettingRepo extends GuiceBuilderRunnable[TestElasticUserSettingRepo] with App { run }
