package runnables

import javax.inject.Inject

import dataaccess.Criterion.CriterionInfix
import dataaccess.RepoTypes.DataSetSettingRepo
import dataaccess.{AscSort, Criterion}
import models.DistributionCalcSpec
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Await._
import scala.concurrent.duration._

class MigrateDataSetSettingToStatsCalc @Inject()(
    repo: DataSetSettingRepo
  ) extends Runnable {

  private val timeout = 120000 millis

  override def run = {
//    val future = repo.find().map { settings =>
//      val newSettings = settings.map { dataSetSetting =>
//        val newCalcSpecs = dataSetSetting.overviewFieldChartTypes.map(fieldChartType =>
//          DistributionCalcSpec(fieldChartType.fieldName, fieldChartType.chartType)
//        )
//        dataSetSetting.copy(statsCalcSpecs = newCalcSpecs)
//      }
//      repo.update(newSettings)
//    }
//    result(future, timeout)
  }
}

object MigrateDataSetSettingToStatsCalc extends GuiceBuilderRunnable[MigrateDataSetSettingToStatsCalc] with App { run }

