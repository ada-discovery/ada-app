package runnables

import javax.inject.Inject

import dataaccess.Criterion.CriterionInfix
import dataaccess.RepoTypes.DataSetSettingRepo
import dataaccess.{AscSort, Criterion}
import models._
import persistence.RepoTypes.DataSetImportRepo
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Await._
import scala.concurrent.duration._
import scala.concurrent.Future

@Deprecated
class MigrateDataSetImportSettingToDataViews @Inject()(
    repo: DataSetImportRepo
  ) extends Runnable {

  private val timeout = 120000 millis

  override def run = {
//    val future = repo.find().map { imports =>
//
//      val dataSetsToUpdate = imports.map { dataSetImport =>
//        dataSetImport.setting.map { setting =>
//          val dataView = DataView(
//            None,
//            "Main",
//            Nil,
//            setting.listViewTableColumnNames,
//            setting.statsCalcSpecs,
//            setting.overviewChartElementGridWidth,
//            true,
//            None
//          )
//
//          dataSetImport match {
//            case x: CsvDataSetImport => x.copy(dataView = Some(dataView))
//            case x: SynapseDataSetImport => x.copy(dataView = Some(dataView))
//            case x: RedCapDataSetImport => x.copy(dataView = Some(dataView))
//            case x: TranSmartDataSetImport => x.copy(dataView = Some(dataView))
//          }
//        }
//      }.flatten
//
//      repo.update(dataSetsToUpdate)
//    }
//
//    result(future, timeout)
  }
}

object MigrateDataSetImportSettingToDataViews extends GuiceBuilderRunnable[MigrateDataSetImportSettingToDataViews] with App { run }

