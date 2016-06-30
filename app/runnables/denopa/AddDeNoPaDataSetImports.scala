package runnables.denopa

import javax.inject.Inject

import persistence.RepoTypes.DataSetImportRepo
import runnables.GuiceBuilderRunnable
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Await.result
import scala.concurrent.Future
import scala.concurrent.duration._

class AddDeNoPaDataSetImports @Inject() (
    repo: DataSetImportRepo,
    imports: DeNoPaDataSetImports
  ) extends Runnable {

  private val timeout = 12 seconds

  override def run = {
    val futures = imports.list.map(repo.save)
    result(Future.sequence(futures), timeout)
  }
}

object AddDeNoPaDataSetImports extends GuiceBuilderRunnable[AddDeNoPaDataSetImports] with App { run }
