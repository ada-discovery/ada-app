package services.datasetimporter

import java.util.Date
import models.CsvDataSetImport
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

private class CsvDataSetImporter extends AbstractDataSetImporter[CsvDataSetImport] {

  override def apply(importInfo: CsvDataSetImport): Future[Unit] = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    val dataRepo = createDataSetAccessor(importInfo).dataSetRepo

    try {
      val lines = createCsvFileLineIterator(
        importInfo.path.get,
        importInfo.charsetName,
        importInfo.eol
      )

      // collect the column names
      val columnNames = dataSetService.getColumnNames(importInfo.delimiter, lines)

      // parse lines
      logger.info(s"Parsing lines...")
      val values = dataSetService.parseLines(columnNames, lines, importInfo.delimiter, importInfo.eol.isDefined)

      // create jsons and field types
      val (jsons, fieldNameAndTypes) = createJsonsWithFieldTypes(columnNames, values)

      for {
        // remove ALL the records from the collection
        _ <- {
          logger.info(s"Deleting the old data set...")
          dataRepo.deleteAll
        }

        // save the jsons
        _ <- {
          logger.info(s"Saving JSONs...")
          dataSetService.saveOrUpdateRecords(dataRepo, jsons)
        }

        // save, or update the dictionary
        _ <- {
          val fieldNameTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
          dataSetService.updateDictionary(importInfo.dataSetId, fieldNameTypeSpecs, true)
        }
      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }
}