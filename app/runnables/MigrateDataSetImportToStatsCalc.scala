package runnables

import javax.inject.Inject

import dataaccess.Criterion.CriterionInfix
import dataaccess.RepoTypes.DataSetSettingRepo
import dataaccess.{AscSort, Criterion}
import models._
import persistence.RepoTypes.DataSetImportRepo
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Await._
import scala.concurrent.duration._

class MigrateDataSetImportToStatsCalc @Inject()(
    repo: DataSetImportRepo
  ) extends Runnable {

  private val timeout = 120000 millis

  override def run = {
//    val future = repo.find().map { imports =>
//      val newImports = imports.map { dataSetImport =>
//        val newSetting = dataSetImport.setting.map { setting =>
//          val newCalcSpecs = setting.overviewFieldChartTypes.map(fieldChartType =>
//            DistributionCalcSpec(fieldChartType.fieldName, fieldChartType.chartType)
//          )
//          setting.copy(statsCalcSpecs = newCalcSpecs)
//        }
//
//        dataSetImport match {
//          case x: CsvDataSetImport => x.copy(setting = newSetting)
//          case x: SynapseDataSetImport => x.copy(setting = newSetting)
//          case x: TranSmartDataSetImport => x.copy(setting = newSetting)
//          case x: RedCapDataSetImport => x.copy(setting = newSetting)
//        }
//      }
//      repo.update(newImports)
//    }
//    result(future, timeout)
  }
}

object MigrateDataSetImportToStatsCalc extends GuiceBuilderRunnable[MigrateDataSetImportToStatsCalc] with App { run }