package runnables.denopa

import javax.inject.Inject

import models.DataSetMetaInfo
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import runnables.{CleanupDataSet, GuiceBuilderRunnable}
import models.DataSetId._

class CleanupDeNoPaBaseline @Inject()(
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(
    denopa_baseline,
    DataSetMetaInfo(None, denopa_curated_baseline, "DeNoPa Curated Baseline", None),
    Some(DeNoPaDataSetSetting.CuratedBaseLine),
    translationRepo
  )

class CleanupDeNoPaFirstVisit @Inject()(
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(
    denopa_firstvisit,
    DataSetMetaInfo(None, denopa_curated_firstvisit, "DeNoPa Curated First Visit", None),
    Some(DeNoPaDataSetSetting.CuratedFirstVisit),
    translationRepo
  )

class CleanupDeNoPaSecondVisit @Inject()(
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(
    denopa_secondvisit,
    DataSetMetaInfo(None, denopa_curated_secondvisit, "DeNoPa Curated Second Visit", None),
    Some(DeNoPaDataSetSetting.CuratedSecondVisit),
    translationRepo
  )

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaFirstVisit] with App { run }
object CleanupDeNoPaSecondVisit extends GuiceBuilderRunnable[CleanupDeNoPaSecondVisit] with App { run }