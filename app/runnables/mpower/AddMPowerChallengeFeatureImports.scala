package runnables.mpower

import javax.inject.Inject

import models.{CsvDataSetImport, DataSetSetting, StorageType}
import persistence.RepoTypes.DataSetImportRepo
import runnables.FutureRunnable
import scala.concurrent.ExecutionContext.Implicits.global

class AddMPowerChallengeFeatureImports @Inject()(dataSetImportRepo: DataSetImportRepo) extends FutureRunnable {

  private val featureIds = Seq(
    9638742,
    9638887,
    9638996,
    9640183,
    9640187,
    9640188,
    9640590,
    9640591,
    9640917,
    9640926,
    9641021,
    9641073,
    9641075,
    9641175,
    9641211,
    9641212,
    9641224,
    9641265,
    9641296,
    9641314,
    9641315,
    9641325,
    9641326,
    9641360,
    9641372,
    9641435,
    9642572
  )

  private val dataSpaceName = "mPower Challenge"
  private val dataSpacePrefix = "mpower_challenge."
  private val folderPath = "/home/peter.banda/Data/mpower_challenge_submission/"
  private val batchSize = 500

  override def runAsFuture = {
    val csvImports = featureIds.map { featureFileId =>
      val dataSetId = dataSpacePrefix + featureFileId
      val dataSetSetting = new DataSetSetting(dataSetId, StorageType.ElasticSearch, "recordId")

      CsvDataSetImport(
        None,
        dataSpaceName,
        dataSetId,
        "Feature Set " + featureFileId,
        Some(folderPath + featureFileId + ".csv"),
        ",",
        None,
        None,
        false,
        true,
        booleanIncludeNumbers = false,
        saveBatchSize = Some(batchSize),
        setting = Some(dataSetSetting)
      )
    }

    dataSetImportRepo.save(csvImports).map(_ => ())
  }
}
