package runnables.luxpark

import javax.inject.Inject

import persistence.RepoTypes.DataSetImportRepo
import scala.concurrent.Await.result
import scala.concurrent.Future
import scala.concurrent.duration._
import runnables.GuiceBuilderRunnable
import scala.concurrent.ExecutionContext.Implicits.global

class AddLuxParkDataSetImports @Inject() (
    repo: DataSetImportRepo,
    imports: LuxParkDataSetImports
  ) extends Runnable {

  private val timeout = 120000 seconds

  override def run = {
    val futures = imports.list.map(repo.save)
    result(Future.sequence(futures), timeout)
  }
}

object AddLuxParkDataSetImports extends GuiceBuilderRunnable[AddLuxParkDataSetImports] with App { run }
