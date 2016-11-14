package services.datasetimporter

import java.util.Date
import javax.inject.Inject

import models.{FieldTypeSpec, FieldTypeId, Field}
import dataaccess.FieldType
import models.synapse.ColumnType
import models.SynapseDataSetImport
import play.api.Configuration
import play.api.libs.json.{Json, JsObject}
import services.{SynapseService, SynapseServiceFactory}
import util.JsonUtil
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Await._
import scala.concurrent.Future

private class SynapseDataSetImporter @Inject() (
    synapseServiceFactory: SynapseServiceFactory,
    configuration: Configuration
  ) extends AbstractDataSetImporter[SynapseDataSetImport] {

  private val synapseDelimiter = ','
  private val synapseEol = "\n"
  private val synapseUsername = configuration.getString("synapse.api.username").get
  private val synapsePassword = configuration.getString("synapse.api.password").get
  private val synapseBulkDownloadAttemptNumber = 4
  private val synapseDefaultBulkDownloadGroupNumber = 5
  private val keyField = "ROW_ID"

  override def apply(importInfo: SynapseDataSetImport): Future[Unit] = {
    val synapseService = synapseServiceFactory(synapseUsername, synapsePassword)

    if (importInfo.downloadColumnFiles)
      for {
        // get the columns of the "file" type
        fileColumns <- synapseService.getTableColumnModels(importInfo.tableId).map(
          _.results.filter(_.columnType == ColumnType.FILEHANDLEID).map(_.toSelectColumn)
        )
        _ <- {
          val fileFieldNames = fileColumns.map { fileColumn =>
            JsonUtil.escapeKey(fileColumn.name.replaceAll("\"", "").trim)
          }
          val fun = updateJsonsFileFields(synapseService, fileFieldNames, importInfo.tableId, importInfo.bulkDownloadGroupNumber) _
          importDataSetAux(importInfo, synapseService, fileFieldNames, Some(fun), importInfo.downloadRecordBatchSize)
        }
      } yield
        ()
    else
      importDataSetAux(importInfo, synapseService, Nil, None, None).map(_ => ())
  }

  def importDataSetAux(
    importInfo: SynapseDataSetImport,
    synapseService: SynapseService,
    fileFieldNames: Traversable[String],
    transformJsonsFun: Option[Seq[JsObject] => Future[(Seq[JsObject])]],
    transformBatchSize: Option[Int]
  ) = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    val dsa = createDataSetAccessor(importInfo)
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val delimiter = synapseDelimiter.toString

    try {
      val lines = {
        logger.info("Downloading CSV table from Synapse...")
        val csvFuture = synapseService.getTableAsCsv(importInfo.tableId)
        val lines = result(csvFuture, timeout).split(synapseEol)
        lines.iterator
      }

      // collect the column names
      val columnNames =  dataSetService.getColumnNames(delimiter, lines)

      // parse lines
      logger.info(s"Parsing lines...")
      val values = dataSetService.parseLines(columnNames, lines, delimiter, true)


      for {
        // get all the fields
        fields <- fieldRepo.find()

        // create jsons and field types
        (jsons, fieldNameAndTypes) = createSynapseJsonsWithFieldTypes(fields, columnNames, fileFieldNames.toSet, values)

        // transform jsons (if needed) and save (and update) the jsons
        _ <- {
          if (transformJsonsFun.isDefined)
            logger.info(s"Saving and transforming JSONs...")
          else
            logger.info(s"Saving JSONs...")

          dataSetService.saveOrUpdateRecords(dataRepo, jsons, Some(keyField), false, transformJsonsFun, transformBatchSize)
        }

        // remove the old records
        _ <- {
          val fieldNameTypeMap = fieldNameAndTypes.toMap
          val fieldType = fieldNameTypeMap.get(keyField).get
          val keys = JsonUtil.project(jsons, keyField).map(fieldType.jsonToValue)
          dataSetService.deleteRecordsExcept(dataRepo, keyField, keys.flatten.toSeq)
        }

        // save, or update the dictionary
        _ <- {
          val fieldNameAndTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
          dataSetService.updateDictionary(importInfo.dataSetId, fieldNameAndTypeSpecs, true)
        }
      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def updateJsonsFileFields(
    synapseService: SynapseService,
    fileFieldNames: Seq[String],
    tableId: String,
    bulkDownloadGroupNumber: Option[Int])(
    jsons: Seq[JsObject]
  ): Future[Seq[JsObject]] = {
    val fileHandleIds = fileFieldNames.map { fieldName =>
      jsons.map(json =>
        JsonUtil.toString(json \ fieldName)
      )
    }.flatten.flatten

    val groupNumber = bulkDownloadGroupNumber.getOrElse(synapseDefaultBulkDownloadGroupNumber)
    if (fileHandleIds.nonEmpty) {
      val groupSize = Math.max(fileHandleIds.size / groupNumber, 1)
      val groups = {
        if (fileHandleIds.size.toDouble / groupSize > groupNumber)
          fileHandleIds.grouped(groupSize + 1)
        else
          fileHandleIds.grouped(groupSize)
      }.toSeq

      logger.info(s"Bulk download of Synapse column data for ${fileHandleIds.size} file handles (split into ${groups.size} groups) initiated.")

      // download the files in a bulk
      val fileHandleIdContentsFutures = groups.par.map { groupFileHandleIds =>
        synapseService.downloadTableFilesInBulk(groupFileHandleIds, tableId, Some(synapseBulkDownloadAttemptNumber))
      }

      Future.sequence(fileHandleIdContentsFutures.toList).map(_.flatten).map { fileHandleIdContents =>
        logger.info(s"Download of ${fileHandleIdContents.size} Synapse column data finished. Updating JSONs with Synapse column data...")
        val fileHandleIdContentMap = fileHandleIdContents.toMap
        // update jsons with new file contents
        val newJsons = jsons.map { json =>
          val fieldNameJsons = fileFieldNames.map { fieldName =>
            JsonUtil.toString(json \ fieldName).map(fileHandleId =>
              (fieldName, Json.parse(fileHandleIdContentMap.get(fileHandleId).get))
            )
          }.flatten
          json ++ JsObject(fieldNameJsons)
        }

        newJsons
      }
    } else
      // no update
      Future(jsons)
  }

  protected def createSynapseJsonsWithFieldTypes(
    fields: Traversable[Field],
    fieldNames: Seq[String],
    fileFieldNames: Set[String],
    values: Seq[Seq[String]]
  ): (Seq[JsObject], Seq[(String, FieldType[_])]) = {
    // get the existing types
    val existingFieldTypeMap: Map[String, FieldType[_]] = fields.map ( field => (field.name, ftf(field.fieldTypeSpec))).toMap

    // infer the new types
    val newFieldTypes = values.transpose.par.map(fti.apply).toList

    // merge the old and new field types
    val fieldNameAndTypesForParsing = fieldNames.zip(newFieldTypes).map { case (fieldName, newFieldType) =>
      val fieldType =
        if (fileFieldNames.contains(fieldName))
          newFieldType
        else
          existingFieldTypeMap.get(fieldName) match {
            case Some(existingFieldType) => mergeEnumTypes(existingFieldType, newFieldType)
            case None => newFieldType
          }
      (fieldName, fieldType)
    }

    // parse strings and create jsons
    val jsons = values.map( vals =>
      JsObject(
        (fieldNameAndTypesForParsing, vals).zipped.map {
          case ((fieldName, fieldType), text) =>
            val jsonValue = fieldType.displayStringToJson(text)
            (fieldName, jsonValue)
        })
    )

    // create the final field types... for file fields report JSON array if the type does not exist
    val finalFieldNameAndTypes = fieldNames.zip(newFieldTypes).map { case (fieldName, newFieldType) =>
      val fieldType: FieldType[_] = existingFieldTypeMap.get(fieldName) match {
        case Some(existingFieldType) => mergeEnumTypes(existingFieldType, newFieldType)
        case None => if (fileFieldNames.contains(fieldName))
          ftf(FieldTypeId.Json, true)
        else
          newFieldType
      }
      (fieldName, fieldType)
    }

    (jsons, finalFieldNameAndTypes)
  }

  private def mergeEnumTypes(oldType: FieldType[_], newType: FieldType[_]): FieldType[_] = {
    val oldTypeSpec = oldType.spec
    val newTypeSpec = newType.spec

    if (oldTypeSpec.fieldType == FieldTypeId.Enum && newTypeSpec.fieldType == FieldTypeId.Enum) {
        val newValues = newTypeSpec.enumValues.get.map(_._2).toBuffer
        val oldValues = oldTypeSpec.enumValues.get.map(_._2)

        val extraValues = newValues.--(oldValues).sorted
        val maxKey = oldTypeSpec.enumValues.get.map(_._1).max
        val extraEnumMap = extraValues.zipWithIndex.map { case (value, index) => (maxKey + index + 1, value)}

        val mergedFieldTypeSpec = oldTypeSpec.copy(enumValues = Some(oldTypeSpec.enumValues.get ++ extraEnumMap))
        ftf(mergedFieldTypeSpec)
    } else
      oldType
  }
}