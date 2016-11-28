package runnables

import javax.inject.Inject

import dataaccess.Criterion.CriterionInfix
import dataaccess.RepoTypes.{UserRepo, DataSetSettingRepo}
import dataaccess.{AscSort, Criterion}
import models.{DataView, DistributionCalcSpec}
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Await._
import scala.concurrent.duration._
import scala.concurrent.Future

class MigrateDataViewUsers @Inject()(
    settingRepo: DataSetSettingRepo,
    userRepo: UserRepo,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  private val timeout = 120000 millis

  override def run = {
    val future = for {
      user <- userRepo.find(
        criteria = Seq("ldapDn" #== "peter.banda"),
        limit = Some(1)
      ).map(_.head)

      settings <- settingRepo.find()

      dataViewRepos = settings.map { dataSetSetting =>
        dsaf(dataSetSetting.dataSetId).map(_.dataViewRepo)
      }.flatten

      _ <- Future.sequence(
        dataViewRepos.map { dataViewRepo =>
          for {
            newDataViews <- dataViewRepo.find().map { dataViews =>
              dataViews.map(_.copy(createdById = user._id))
            }
            _ <- dataViewRepo.update(newDataViews)
          } yield
            ()
        }
      )
    } yield
      ()

    result(future, timeout)
  }
}

object MigrateDataViewUsers extends GuiceBuilderRunnable[MigrateDataViewUsers] with App { run }

