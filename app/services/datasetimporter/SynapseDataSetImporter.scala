package services.datasetimporter

import java.util.Date
import javax.inject.Inject

import models.{Field, FieldTypeId, FieldTypeSpec}
import dataaccess.{FieldType, FieldTypeHelper}
import models.synapse._
import models.SynapseDataSetImport
import play.api.Configuration
import play.api.libs.json.{JsArray, JsObject, Json}
import services.{SynapseService, SynapseServiceFactory}
import dataaccess.JsonUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await._
import scala.concurrent.{Await, Future}

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

  private val synapseFtf = FieldTypeHelper.fieldTypeFactory(FieldTypeHelper.nullAliases ++ Set("nan"))
  private val fti = FieldTypeHelper.fieldTypeInferrerFactory(synapseFtf).apply

  private val prefixSuffixSeparators = Seq(
    ("\"[\"\"", "\"\"]\""),
    ("\"[", "]\""),
    ("\"", "\"")
  )

  override def apply(importInfo: SynapseDataSetImport): Future[Unit] = {
    val synapseService = synapseServiceFactory(synapseUsername, synapsePassword)

    def escapedColumnName(column: ColumnModel) =
      JsonUtil.escapeKey(column.name.replaceAll("\"", "").trim)

    val futureImport = if (importInfo.downloadColumnFiles)
      for {
        // get the columns of the "file" and "entity" type (max 1)
        (entityColumnName, fileFieldNames) <- synapseService.getTableColumnModels(importInfo.tableId).map { columnModels =>
          val fileColumns = columnModels.results.filter(_.columnType == ColumnType.FILEHANDLEID).map(escapedColumnName)
          val entityColumn = columnModels.results.find(_.columnType == ColumnType.ENTITYID).map(escapedColumnName)
          (entityColumn, fileColumns)
        }

        _ <- {
          val bulkDownloadGroupNumber = importInfo.bulkDownloadGroupNumber.getOrElse(synapseDefaultBulkDownloadGroupNumber)

          val fun = updateJsonsFileFields(
            synapseService,
            fileFieldNames,
            entityColumnName,
            importInfo.tableId,
            bulkDownloadGroupNumber,
            Some(synapseBulkDownloadAttemptNumber)
          )_

          importDataSetAux(importInfo, synapseService, fileFieldNames, Some(fun), importInfo.batchSize)
        }
      } yield
        ()
    else
      importDataSetAux(importInfo, synapseService, Nil, None, importInfo.batchSize).map(_ => ())

    futureImport
//    futureImport.map(_ => synapseService.close)
  }

  def importDataSetAux(
    importInfo: SynapseDataSetImport,
    synapseService: SynapseService,
    fileFieldNames: Traversable[String],
    transformJsonsFun: Option[Seq[JsObject] => Future[(Seq[JsObject])]],
    batchSize: Option[Int]
  ) = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    val dsa = createDataSetAccessor(importInfo)
    val fieldRepo = dsa.fieldRepo
    val delimiter = synapseDelimiter.toString

    try {
      for {
        csv <- {
          logger.info("Downloading CSV table from Synapse...")
          synapseService.getTableAsCsv(importInfo.tableId)
        }

        // get all the fields
        fields <- fieldRepo.find()

        // create jsons and field types
        (jsons, fieldNameAndTypes) = {
          logger.info(s"Parsing lines and inferring types...")
          extractJsonsAndInferFieldsFromCSV(csv, delimiter, fields, fileFieldNames)
        }

        // save, or update the dictionary
        _ <- {
          val fieldNameAndTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
          dataSetService.updateDictionary(importInfo.dataSetId, fieldNameAndTypeSpecs, false, true)
        }

        // since we possible changed the dictionary (the data structure) we need to update the data set repo
        _ <- dsa.updateDataSetRepo

        // get the new data set repo
        dataRepo = dsa.dataSetRepo

        // transform jsons (if needed) and save (and update) the jsons
        _ <- {
          if (transformJsonsFun.isDefined)
            logger.info(s"Saving and transforming JSONs...")
          else
            logger.info(s"Saving JSONs...")

          dataSetService.saveOrUpdateRecords(dataRepo, jsons, Some(keyField), false, transformJsonsFun, batchSize)
        }

        // remove the old records
        _ <- {
          val fieldNameTypeMap = fieldNameAndTypes.toMap
          val fieldType = fieldNameTypeMap.get(keyField).get
          val keys = JsonUtil.project(jsons, keyField).map(fieldType.jsonToValue)
          dataSetService.deleteRecordsExcept(dataRepo, keyField, keys.flatten.toSeq)
        }
      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def extractJsonsAndInferFieldsFromCSV(
    csv: String,
    delimiter: String,
    fields: Traversable[Field],
    fileFieldNames: Traversable[String]
  ): (Seq[JsObject], Seq[(String, FieldType[_])]) = {
    // split by lines
    val lines = csv.split(synapseEol).iterator

    // collect the column names
    val columnNames = dataSetService.getColumnNames(delimiter, lines)

    // parse lines
    val values = dataSetService.parseLines(columnNames, lines, delimiter, true, prefixSuffixSeparators)

    // create jsons and field types
    createSynapseJsonsWithFieldTypes(columnNames, fileFieldNames.toSet, values.toSeq, fields)
  }

  protected def updateJsonsFileFields(
    synapseService: SynapseService,
    fileFieldNames: Seq[String],
    entityColumnName: Option[String],
    tableId: String,
    bulkDownloadGroupNumber: Int,
    bulkDownloadAttemptNumber: Option[Int])(
    jsons: Seq[JsObject]
  ): Future[Seq[JsObject]] = {
    val fileHandleEntityIds = fileFieldNames.flatMap( fieldName =>
      jsons.flatMap { json =>
        JsonUtil.toString(json \ fieldName).map { fileHandleId =>
          val entityId = entityColumnName.flatMap(name => JsonUtil.toString(json \ name))
          (fileHandleId, entityId)
        }
      }
    )

    if (fileHandleEntityIds.nonEmpty) {
      val groupSize = Math.max(fileHandleEntityIds.size / bulkDownloadGroupNumber, 1)
      val groups = {
        if (fileHandleEntityIds.size.toDouble / groupSize > bulkDownloadGroupNumber)
          fileHandleEntityIds.grouped(groupSize + 1)
        else
          fileHandleEntityIds.grouped(groupSize)
      }.toSeq

      logger.info(s"Bulk download of Synapse column data for ${fileHandleEntityIds.size} file handles (split into ${groups.size} groups) initiated.")

      // download the files in a bulk
      val fileHandleIdContentsFutures = groups.par.map { groupFileHandleEntityIds =>
        val assocs = groupFileHandleEntityIds.map { case (handleId, entityId) =>
          entityId match {
            case None => FileHandleAssociation(FileHandleAssociateType.TableEntity, handleId, tableId)
            case Some(entityId) =>  FileHandleAssociation(FileHandleAssociateType.FileEntity, handleId, entityId)
          }

        }
        synapseService.downloadFilesInBulk(assocs, bulkDownloadAttemptNumber)
      }

      Future.sequence(fileHandleIdContentsFutures.toList).map(_.flatten).map { fileHandleIdContents =>
        logger.info(s"Download of ${fileHandleIdContents.size} Synapse column data finished. Updating JSONs with Synapse column data...")
        val fileHandleIdContentMap = fileHandleIdContents.toMap
        // update jsons with new file contents
        val newJsons = jsons.map { json =>
          val fieldNameJsons = fileFieldNames.flatMap( fieldName =>
            JsonUtil.toString(json \ fieldName).map { fileHandleId =>
              val fileContent = fileHandleIdContentMap.get(fileHandleId).get
              val columnJson =
                if (entityColumnName.isDefined) {
                  val (jsons, _) = extractJsonsAndInferFieldsFromCSV(fileContent, "\t", Nil, Nil)
                  JsArray(jsons)
                } else
                  Json.parse(fileContent)

              (fieldName, columnJson)
            }
          )
          json ++ JsObject(fieldNameJsons)
        }

        newJsons
      }
    } else
    // no update
      Future(jsons)
  }

  private def createSynapseJsonsWithFieldTypes(
    fieldNames: Seq[String],
    fileFieldNames: Set[String],
    values: Seq[Seq[String]],
    fields: Traversable[Field]
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