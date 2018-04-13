package runnables.luxpark

import javax.inject.Inject

import persistence.RepoTypes.DataSetImportRepo

import scala.concurrent.Future
import runnables.{FutureRunnable, GuiceBuilderRunnable}

import scala.concurrent.ExecutionContext.Implicits.global

class AddLuxParkDataSetImports @Inject() (
    repo: DataSetImportRepo,
    imports: LuxParkDataSetImports
  ) extends FutureRunnable {

  override def runAsFuture =
    Future.sequence(imports.list.map(repo.save)).map(_ => ())
}

object AddLuxParkDataSetImports extends GuiceBuilderRunnable[AddLuxParkDataSetImports] with App { run }
