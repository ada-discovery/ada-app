package runnables.denopa

import javax.inject.Inject
import dataaccess.DataSetMetaInfo
import persistence.RepoTypes._
import runnables.{CleanupDataSet, GuiceBuilderRunnable}
import runnables.DataSetId._

class CleanupDeNoPaBaseline @Inject()(
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(
    denopa_raw_clinical_baseline,
    DataSetMetaInfo(None, denopa_clinical_baseline, "Clinical Baseline", 0, false, None),
    Some(DeNoPaDataSetSettings.ClinicalBaseline),
    translationRepo
  )

class CleanupDeNoPaFirstVisit @Inject()(
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(
    denopa_raw_clinical_first_visit,
    DataSetMetaInfo(None, denopa_clinical_first_visit, "Clinical First Visit", 1, false, None),
    Some(DeNoPaDataSetSettings.ClinicalFirstVisit),
    translationRepo
  )

class CleanupDeNoPaSecondVisit @Inject()(
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(
    denopa_raw_clinical_second_visit,
    DataSetMetaInfo(None, denopa_clinical_second_visit, "Clinical Second Visit", 2, false, None),
    Some(DeNoPaDataSetSettings.ClinicalSecondVisit),
    translationRepo
  )

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaFirstVisit] with App { run }
object CleanupDeNoPaSecondVisit extends GuiceBuilderRunnable[CleanupDeNoPaSecondVisit] with App { run }