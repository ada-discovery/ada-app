package runnables

import javax.inject.Inject

import dataaccess.Criterion.CriterionInfix
import dataaccess.RepoTypes.DataSetSettingRepo
import dataaccess.{AscSort, Criterion}
import models.{DataView, DistributionCalcSpec}
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Await._
import scala.concurrent.duration._
import scala.concurrent.Future

class MigrateDataSetSettingToDataViews @Inject()(
    repo: DataSetSettingRepo,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  private val timeout = 120000 millis

  override def run = {
    val future = repo.find().map { settings =>

      val futures = settings.map { dataSetSetting =>
        dsaf(dataSetSetting.dataSetId) match {
          case Some(dsa) => {
            val dataView = DataView(
              None,
              "Main",
              Nil,
              dataSetSetting.listViewTableColumnNames,
              dataSetSetting.statsCalcSpecs,
              dataSetSetting.overviewChartElementGridWidth,
              true,
              None
            )

            dsa.dataViewRepo.save(dataView)
          }
          case None => Future(())
        }
      }

      Future.sequence(futures)
    }
    result(future, timeout)
  }
}

object MigrateDataSetSettingToDataViews extends GuiceBuilderRunnable[MigrateDataSetSettingToDataViews] with App { run }

