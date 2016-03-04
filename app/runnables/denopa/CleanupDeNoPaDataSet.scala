package runnables.denopa

import javax.inject.Inject

import models.DataSetMetaInfo
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import runnables.{CleanupDataSet, GuiceBuilderRunnable}
import models.DataSetId._

class CleanupDeNoPaBaseline @Inject()(
    dsaf: DataSetAccessorFactory,
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(
    denopa_baseline,
    DataSetMetaInfo(None, denopa_curated_baseline, "DeNoPa Curated Baseline"),
    dsaf,
    translationRepo
  )

class CleanupDeNoPaFirstVisit @Inject()(
    dsaf: DataSetAccessorFactory,
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(
    denopa_firstvisit,
    DataSetMetaInfo(None, denopa_curated_firstvisit, "DeNoPa Curated First Visit"),
    dsaf,
    translationRepo
  )

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }