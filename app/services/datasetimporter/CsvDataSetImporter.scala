package services.datasetimporter

import java.util.Date
import dataaccess.{FieldTypeHelper, FieldTypeFactory, FieldTypeInferrerFactory, FieldType}
import dataaccess.RepoTypes.JsonCrudRepo
import models.CsvDataSetImport
import persistence.dataset.DataSetAccessor
import play.api.libs.json.JsObject
import util.seqFutures
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
            saveJsonsWithTypeInference(dsa, columnNames, values, importInfo)
          else
            saveJsonsWithoutTypeInference(dsa, columnNames, values, importInfo.saveBatchSize)
      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def saveJsonsWithoutTypeInference(
    dsa: DataSetAccessor,
    columnNames: Seq[String],
    values: Iterator[Seq[String]],
    saveBatchSize: Option[Int]
  ): Future[Unit] = {
    // create jsons and field types
    logger.info(s"Creating JSONs...")
    val (jsons, fieldNameAndTypes) = createJsonsWithStringFieldTypes(columnNames, values)

    for {
     // save, or update the dictionary
      _ <- {
        val fieldNameTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
        dataSetService.updateDictionary(dsa.fieldRepo, fieldNameTypeSpecs, true, true)
      }

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- dsa.updateDataSetRepo

      // get the new data set repo
      dataRepo = dsa.dataSetRepo

      // remove ALL the records from the collection
      _ <- {
        logger.info(s"Deleting the old data set...")
        dsa.dataSetRepo.deleteAll
      }

      // save the jsons
      _ <- {
        logger.info(s"Saving JSONs...")
        saveBatchSize match {
          case Some(saveBatchSize) =>
            seqFutures(
              jsons.grouped(saveBatchSize))(
              dataRepo.save
            )

          case None =>
            Future.sequence(
              jsons.map(dataRepo.save)
            )
        }
      }
    } yield
      ()
  }

  private def saveJsonsWithTypeInference(
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
    val (jsons, fieldNameAndTypes) = createJsonsWithFieldTypes(columnNames, values.toSeq, fti)

    for {
      // save, or update the dictionary
      _ <- {
        val fieldNameTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
        dataSetService.updateDictionary(dsa.fieldRepo, fieldNameTypeSpecs, true, true)
      }

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- dsa.updateDataSetRepo

      // get the new data set repo
      dataRepo = dsa.dataSetRepo

      // remove ALL the records from the collection
      _ <- {
        logger.info(s"Deleting the old data set...")
        dataRepo.deleteAll
      }

      // save the jsons
      _ <- {
        logger.info(s"Saving JSONs...")
        dataSetService.saveOrUpdateRecords(dataRepo, jsons,  None, false, None, importInfo.saveBatchSize)
      }
    } yield
      ()
  }
}