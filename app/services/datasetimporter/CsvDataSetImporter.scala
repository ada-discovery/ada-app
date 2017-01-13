package services.datasetimporter

import java.util.Date
import dataaccess.FieldType
import dataaccess.RepoTypes.JsonCrudRepo
import models.CsvDataSetImport
import persistence.dataset.DataSetAccessor
import play.api.libs.json.JsObject
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
        // save the jsons and get the field types
        fieldNameAndTypes <-
          if (importInfo.inferFieldTypes)
            saveJsonsWithTypeInference(columnNames, values, dsa)
          else
            saveJsonsWithoutTypeInference(columnNames, values, dsa)
      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def saveJsonsWithoutTypeInference(
    columnNames: Seq[String],
    values: Iterator[Seq[String]],
    dsa: DataSetAccessor
  ): Future[Seq[(String, FieldType[_])]] = {
    // create jsons and field types
    logger.info(s"Creating JSONs...")
    val (jsons, fieldNameAndTypes) = createDummyJsonsWithFieldTypes(columnNames, values)

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
      _ <- Future.sequence {
        logger.info(s"Saving JSONs...")
        jsons.map(dataRepo.save)
      }
    } yield
      fieldNameAndTypes
  }

  private def saveJsonsWithTypeInference(
    columnNames: Seq[String],
    values: Iterator[Seq[String]],
    dsa: DataSetAccessor
  ): Future[Seq[(String, FieldType[_])]] = {

    // create jsons and field types
    logger.info(s"Creating JSONs...")
    val (jsons, fieldNameAndTypes) = createJsonsWithFieldTypes(columnNames, values.toSeq)

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
        dataSetService.saveOrUpdateRecords(dataRepo, jsons)
      }
    } yield
      fieldNameAndTypes
  }

  protected def createDummyJsonsWithFieldTypes(
    fieldNames: Seq[String],
    values: Iterator[Seq[String]]
  ): (Iterator[JsObject], Seq[(String, FieldType[_])]) = {
    val fieldTypes = fieldNames.map(_ => ftf.stringScalar)

    val jsons = values.map( vals =>
      JsObject(
        (fieldNames, fieldTypes, vals).zipped.map {
          case (fieldName, fieldType, text) =>
            val jsonValue = fieldType.displayStringToJson(text)
            (fieldName, jsonValue)
        })
    )

    (jsons, fieldNames.zip(fieldTypes))
  }
}