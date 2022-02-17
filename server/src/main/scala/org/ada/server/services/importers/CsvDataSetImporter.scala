package org.ada.server.services.importers

import org.ada.server.dataaccess.dataset.DataSetAccessor
import org.ada.server.field.FieldTypeHelper
import org.ada.server.models.dataimport.CsvDataSetImport
import org.ada.server.util.ManageResource.{closeResource, closeResourceWithFutureFailed}

import java.util.Date
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.BufferedSource

private class CsvDataSetImporter extends AbstractDataSetImporter[CsvDataSetImport] {

  private val quotePrefixSuffix = ("\"", "\"")

  override def runAsFuture(importInfo: CsvDataSetImport): Future[Unit] = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")
    var source: BufferedSource = null
    try {
      source = getResource(importInfo.path.get, importInfo.charsetName)
      val lines = createCsvFileLineIterator(importInfo.eol, source)
      // collect the column names and labels
      val columnsInfo = dataSetService.getColumnsInfo(importInfo.delimiter, lines)
      // parse lines
      logger.info(s"Parsing lines...")
      val prefixSuffixSeparators = if (importInfo.matchQuotes) Seq(quotePrefixSuffix) else Nil
      val values = dataSetService.parseLines(columnsInfo, lines, importInfo.delimiter, importInfo.eol.isDefined, prefixSuffixSeparators)

      val saveRes = for {
        // create/retrieve a dsa
        dsa <- createDataSetAccessor(importInfo)

        // save the jsons and dictionary
        _ <-
          if (importInfo.inferFieldTypes)
            saveDataAndDictionaryWithTypeInference(dsa, columnsInfo.namesAndLabels, values, importInfo)
          else
            saveStringsAndDictionaryWithoutTypeInference(dsa, columnsInfo.namesAndLabels, values, importInfo.saveBatchSize)
      } yield
        closeResource(source)

      saveRes.recoverWith{ case ex: Exception => closeResourceWithFutureFailed(ex, source)}

    } catch {
      case e: Exception => closeResourceWithFutureFailed(e, source)
    }
  }

  private def saveDataAndDictionaryWithTypeInference(
    dsa: DataSetAccessor,
    columnNamesAndLabels: Seq[(String, String)],
    values: Iterator[Seq[String]],
    importInfo: CsvDataSetImport
  ): Future[Unit] = {
    // infer field types and create JSONSs
    logger.info(s"Inferring field types and creating JSONs...")

    val arrayDelimiter = importInfo.arrayDelimiter.getOrElse(FieldTypeHelper.arrayDelimiter)
    val maxEnumValuesCount = importInfo.inferenceMaxEnumValuesCount.getOrElse(FieldTypeHelper.maxEnumValuesCount)
    val minAvgValuesPerEnum = importInfo.inferenceMinAvgValuesPerEnum.getOrElse(FieldTypeHelper.minAvgValuesPerEnum)
    val nullAliases = FieldTypeHelper.nullAliasesOrDefault(importInfo.explicitNullAliases)

    val fti = FieldTypeHelper.fieldTypeInferrerFactory(
      nullAliases = nullAliases,
      booleanIncludeNumbers = importInfo.booleanIncludeNumbers,
      maxEnumValuesCount = maxEnumValuesCount,
      minAvgValuesPerEnum = minAvgValuesPerEnum,
      arrayDelimiter = arrayDelimiter
    ).ofString

    saveStringsAndDictionaryWithTypeInference(dsa, columnNamesAndLabels, values, importInfo.saveBatchSize, fti)
  }
}