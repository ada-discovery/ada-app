package runnables.core

import javax.inject.Inject

import _root_.util.getListOfFiles
import models.{CsvDataSetImport, DataSetSetting, StorageType}
import persistence.RepoTypes.DataSetImportRepo
import runnables.InputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class AddCsvDataSetImportsFromFolder @Inject()(
    dataSetImportRepo: DataSetImportRepo
  ) extends InputFutureRunnable[AddCsvDataSetImportsFromFolderSpec] {

  override def runAsFuture(spec: AddCsvDataSetImportsFromFolderSpec) = {
    val csvImports = getListOfFiles(spec.folderPath).map { importFile =>
      val importFileName = importFile.getName
      val importFileNameWoExt = importFileName.replaceAll("\\.[^.]*$", "")

      val dataSetId = spec.dataSetIdPrefix + "." + importFileNameWoExt
      val dataSetName = spec.dataSetNamePrefix + " " + importFileNameWoExt

      val dataSetSetting = new DataSetSetting(dataSetId, spec.storageType, spec.defaultDistributionFieldName)

      CsvDataSetImport(
        None,
        spec.dataSpaceName,
        dataSetId,
        dataSetName,
        Some(importFile.getAbsolutePath),
        ",",
        None,
        None,
        true,
        true,
        booleanIncludeNumbers = false,
        saveBatchSize = spec.batchSize,
        setting = Some(dataSetSetting)
      )
    }

    dataSetImportRepo.save(csvImports).map(_ => ())
  }

  override def inputType = typeOf[AddCsvDataSetImportsFromFolderSpec]
}

case class AddCsvDataSetImportsFromFolderSpec(
  folderPath: String,
  dataSpaceName: String,
  dataSetIdPrefix: String,
  dataSetNamePrefix: String,
  delimiter: String,
  storageType: StorageType.Value,
  defaultDistributionFieldName: String,
  batchSize: Option[Int]
)