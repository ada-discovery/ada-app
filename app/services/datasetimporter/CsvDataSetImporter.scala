package services.datasetimporter

import java.util.Date

import dataaccess._
import models.CsvDataSetImport
import persistence.dataset.DataSetAccessor

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

private class CsvDataSetImporter extends AbstractDataSetImporter[CsvDataSetImport] {

  override def apply(importInfo: CsvDataSetImport): Future[Unit] = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    try {
      val dsa = createDataSetAccessor(importInfo)

      val lines = createCsvFileLineIterator(
        importInfo.path.get,
        importInfo.charsetName,
        importInfo.eol
      )

      // collect the column names
      val columnNames = dataSetService.getColumnNames(importInfo.delimiter, lines)

      // parse lines
      logger.info(s"Parsing lines...")
      val values = dataSetService.parseLines(columnNames, lines, importInfo.delimiter, importInfo.eol.isDefined, importInfo.matchQuotes)

      for {
        // save the jsons and dictionary
        _ <-
          if (importInfo.inferFieldTypes)
            saveDataAndDictionaryWithTypeInference(dsa, columnNames, values, importInfo)
          else
            saveDataAndDictionaryWithoutTypeInference(dsa, columnNames, values, importInfo.saveBatchSize)
      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def saveDataAndDictionaryWithTypeInference(
    dsa: DataSetAccessor,
    columnNames: Seq[String],
    values: Iterator[Seq[String]],
    importInfo: CsvDataSetImport
  ): Future[Unit] = {
    // infer field types and create JSONSs
    logger.info(s"Inferring field types and creating JSONs...")

    val fti =
      if (importInfo.inferenceMaxEnumValuesCount.isDefined || importInfo.inferenceMinAvgValuesPerEnum.isDefined) {
        Some(
          FieldTypeInferrerFactory(
            FieldTypeHelper.fieldTypeFactory,
            importInfo.inferenceMaxEnumValuesCount.getOrElse(FieldTypeHelper.maxEnumValuesCount),
            importInfo.inferenceMinAvgValuesPerEnum.getOrElse(FieldTypeHelper.minAvgValuesPerEnum),
            FieldTypeHelper.arrayDelimiter
          ).apply
        )
      } else
        None

    saveDataAndDictionaryWithTypeInference(dsa, columnNames, values, importInfo.saveBatchSize, fti)
  }
}