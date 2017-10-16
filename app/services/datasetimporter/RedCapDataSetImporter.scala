package services.datasetimporter

import java.util.Date
import javax.inject.Inject

import dataaccess.RepoTypes.CategoryRepo
import dataaccess.RepoTypes.FieldRepo
import dataaccess._
import models.{Field, FieldTypeId, FieldTypeSpec, Category}
import models.redcap.{Metadata, FieldType => RCFieldType}
import models.{AdaParseException, AdaException, RedCapDataSetImport}
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.{RedCapServiceFactory, RedCapService}
import dataaccess.Criterion.Infix
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

private class RedCapDataSetImporter @Inject() (
    redCapServiceFactory: RedCapServiceFactory
  ) extends AbstractDataSetImporter[RedCapDataSetImport] {

  private val choicesDelimiter = "\\|"
  private val choiceKeyValueDelimiter = ","
  private val visitFieldName = "redcap_event_name"
  private val visitLabel = "Visit"
  private val visitPrefix = "visit"
  private val armPrefix = "arm"

  private val defaultSaveBatchSize = 20

  override def apply(importInfo: RedCapDataSetImport): Future[Unit] = {
    logger.info(new Date().toString)

    if (importInfo.importDictionaryFlag)
      logger.info(s"Import of data set and dictionary '${importInfo.dataSetName}' initiated.")
    else
      logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    // Red cap service to pull the data from
    val redCapService = redCapServiceFactory(importInfo.url, importInfo.token)

    // Data repo to store the data to
    val dsa = createDataSetAccessor(importInfo)
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo

    val stringFieldType = ftf.stringScalar

    val batchSize = importInfo.saveBatchSize.getOrElse(defaultSaveBatchSize)

    // helper functions to parse jsons
    def displayJsonToJson[T](fieldType: FieldType[T], json: JsReadable): JsValue = {
      val value = fieldType.displayJsonToValue(json)
      fieldType.valueToJson(value)
    }

    def displayJsonToJsonEnum(fieldType: FieldType[_], json: JsReadable) = {
      val enumStringValue = stringFieldType.displayJsonToValue(json)
      enumStringValue match {
        case Some(string) =>
          if (ConversionUtil.isInt(string)) {
            JsNumber(string.toInt)
          } else {
            displayJsonToJson(fieldType, json)
          }
        case None => JsNull
      }
    }

    for {

      // get the data from a given red cap service
      records <- {
        logger.info("Downloading records from REDCap...")
        redCapService.listRecords("cdisc_dm_usubjd", "")
      }

      // import dictionary (if needed) otherwise use an existing one (should exist)
      fields <- importOrGetDictionary(importInfo, redCapService, fieldRepo, categoryRepo, records)

      // create a field-name-type map for a quick lookup
      fieldNameTypeMap = {
        val map: Map[String, FieldType[_]] = fields.map(field => (field.name, ftf(field.fieldTypeSpec) : FieldType[_])).toSeq.toMap
        map
      }

      // get the ids of the categories that need to be inherited from the first visit
      categoryIdsToInherit <-
        categoryRepo.find(Seq("name" #-> importInfo.categoriesToInheritFromFirstVisit)).map(_.map(_._id.get))

      // obtain the names of the fiels that need to be inherited
      fieldNamesToInherit = {
        val categoryIdsToInheritSet = categoryIdsToInherit.toSet

        val fieldsToInherit = fields.filter { field =>
          field.categoryId.map { categoryId =>
            categoryIdsToInheritSet.contains(categoryId)
          }.getOrElse(false)
        }

        fieldsToInherit.map(_.name)
      }

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- dsa.updateDataSetRepo

      // get the new data set repo
      dataRepo = dsa.dataSetRepo

      // delete all the records
      _ <- {
        logger.info(s"Deleting the old data set...")
        dataRepo.deleteAll
      }

      // get the records with inferred types
      newRecords: Traversable[JsObject] = records.map { record =>
        val newJsValues = record.fields.map { case (fieldName, jsValue) =>
          val newJsValue = fieldNameTypeMap.get(fieldName) match {
            case Some(fieldType) => try {
              fieldType.spec.fieldType match {
                case FieldTypeId.Enum => displayJsonToJsonEnum(fieldType, jsValue)
                case _ => displayJsonToJson(fieldType, jsValue)
              }
            } catch {
              case e: Exception => throw new AdaException(s"JSON value '$jsValue' of the field '$fieldName' cannot be processed.", e)
            }
            // TODO: this shouldn't be like that... if we don't have a field in the dictionary, we should discard it
            case None => jsValue
          }
          (fieldName, newJsValue)
        }

        JsObject(newJsValues)
      }

      // inherit the values
      inheritedRecords =
        importInfo.setting.map { setting =>
          if (fieldNamesToInherit.nonEmpty)
            inheritFieldValues(newRecords, setting.keyFieldName, fieldNameTypeMap, fieldNamesToInherit)
          else
            newRecords
        }.getOrElse(
          newRecords
        )

      // save the records
      _ <- dataSetService.saveOrUpdateRecords(dataRepo, inheritedRecords.toSeq, batchSize = Some(batchSize))
    } yield
      if (importInfo.importDictionaryFlag)
        messageLogger.info(s"Import of data set and dictionary '${importInfo.dataSetName}' successfully finished.")
      else
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
  }

  private def importOrGetDictionary(
    importInfo: RedCapDataSetImport,
    redCapService: RedCapService,
    fieldRepo: FieldRepo,
    categoryRepo: CategoryRepo,
    records: Traversable[JsObject]
  ): Future[Traversable[Field]] = {
    if (importInfo.importDictionaryFlag) {
      logger.info(s"RedCap dictionary inference and import for data set '${importInfo.dataSetId}' initiated.")

      val fieldsFuture = importAndInferRedCapDictionary(importInfo.dataSetId, redCapService, fieldRepo, categoryRepo, records)

      fieldsFuture.map { fields =>
        messageLogger.info(s"RedCap dictionary inference and import for data set '${importInfo.dataSetId}' successfully finished.")
        fields
      }
    } else {
      logger.info(s"RedCap dictionary import disabled, using an existing dictionary.")
      fieldRepo.find().map(fields =>
        if (fields.nonEmpty) {
          fields
        } else {
          val message = s"No dictionary found for the data set '${importInfo.dataSetId}'. Run the REDCap data set import again with the 'import dictionary' option."
          messageLogger.error(message)
          throw new AdaException(message)
        }
      )
    }
  }

  private def inheritFieldValues(
    records: Traversable[JsObject],
    keyFieldName: String,
    fieldNameTypeMap: Map[String, FieldType[_]],
    fieldNamesToInherit: Traversable[String]
  ): Traversable[JsObject] = {
    logger.info("Inheriting fields from the first visit...")

    val visitField = fieldNameTypeMap.get(visitFieldName).get

    def visitValue(json: JsObject): String =
      visitField.jsonToDisplayString(json \ visitFieldName)

    def inheritFields(
      json: JsObject,
      visit1Json: JsObject
    ): JsObject = {
      val inheritedJsValues = fieldNamesToInherit.map { fieldName =>
        val fieldType = fieldNameTypeMap.get(fieldName).get
        val jsValue = (json \ fieldName).get

        val inheritedJsValue = if (jsValue == JsNull) (visit1Json \ fieldName).get else jsValue
        (fieldName, inheritedJsValue)
      }.toSeq
      json.++(JsObject(inheritedJsValues))
    }

    val visit1Id = s"${visitPrefix}_1_${armPrefix}_1"
    val keyField = fieldNameTypeMap.get(keyFieldName).get

    records.groupBy(json => keyField.jsonToValue(json \ keyFieldName)).map { case (_, groupRecords) =>

      val visit1RecordOption = groupRecords.find( record => visitValue(record).equals(visit1Id) )

      visit1RecordOption.map { visit1Record =>
        groupRecords.map { record =>
          if (!visitValue(record).equals(visit1Id))
            inheritFields(record, visit1Record)
          else
            record
        }
      }.getOrElse(groupRecords)
    }.flatten
  }

  private def importAndInferRedCapDictionary(
    dataSetId: String,
    redCapService: RedCapService,
    fieldRepo: FieldRepo,
    categoryRepo: CategoryRepo,
    records: Traversable[JsObject]
  ): Future[Traversable[Field]] = {

    def displayJsonToDisplayString[T](fieldType: FieldType[T], json: JsReadable): String = {
      val value = fieldType.displayJsonToValue(json)
      fieldType.valueToDisplayString(value)
    }

    val fieldNames = records.map(_.keys).flatten.toSet
    val stringFieldType = ftf.stringScalar
    val inferredFieldNameTypeMap: Map[String, FieldType[_]] =
      fieldNames.map { fieldName =>
        val stringValues = records.map(record =>
          displayJsonToDisplayString(stringFieldType, (record \ fieldName))
        )
        (fieldName, defaultFti(stringValues))
      }.toMap

    // TODO: optimize this... introduce field groups to speed up inference
    def inferDictionary(
      metadatas: Seq[Metadata],
      nameCategoryIdMap: Map[String, BSONObjectID]
    ): Traversable[Field] =
      metadatas.filter(metadata => inferredFieldNameTypeMap.keySet.contains(metadata.field_name)).par.map { metadata =>
        val fieldName = metadata.field_name
        val inferredFieldType: FieldType[_] = inferredFieldNameTypeMap.get(fieldName).get
        val inferredType = inferredFieldType.spec

        def enumOrDoubleOrString: FieldTypeSpec = try {
          FieldTypeSpec(FieldTypeId.Enum, false, getEnumValues(metadata))
        } catch {
          case e: AdaParseException => {
            try {
              getDoubles(metadata)
              logger.warn(s"The field '$fieldName' has floating part(s) in the enum list and so will be treated as Double.")
              FieldTypeSpec(FieldTypeId.Double)
            } catch {
              case e: AdaParseException => {
                logger.warn(s"The field '$fieldName' has strings in the enum list and so will be treated as String.")
                FieldTypeSpec(FieldTypeId.String)
              }
            }
          }
        }

        val fieldTypeSpec = metadata.field_type match {
          case RCFieldType.radio => enumOrDoubleOrString
          case RCFieldType.checkbox => enumOrDoubleOrString
          case RCFieldType.dropdown => enumOrDoubleOrString
          case RCFieldType.calc => inferredType
          case RCFieldType.slider => inferredType
          case RCFieldType.text => inferredType
          case RCFieldType.descriptive => inferredType
          case RCFieldType.yesno => FieldTypeSpec(FieldTypeId.Boolean)
          case RCFieldType.truefalse => FieldTypeSpec(FieldTypeId.Boolean)
          case RCFieldType.notes => inferredType
          case RCFieldType.file => inferredType
        }

        val categoryId = nameCategoryIdMap.get(metadata.form_name)
        val stringEnumValues = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to) })
        Field(metadata.field_name, Some(metadata.field_label), fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnumValues, None, None, None, Nil, categoryId)
      }.toList

    for {
      // obtain the RedCAP metadata
      metadatas <- redCapService.listMetadatas("field_name", "")

      // save the obtained categories and return category names with ids
      categoryNameIds <- Future.sequence {
        metadatas.map(_.form_name).toSet.map { categoryName: String =>
          categoryRepo.find(Seq("name" #== categoryName)).flatMap { categories =>
            val id: Future[BSONObjectID] =
              categories.headOption match {
                case Some(category) => Future(category._id.get)
                case None => categoryRepo.save(new Category(categoryName))
              }

            id.map((categoryName, _))
          }
        }
      }

      // fields
      newFields = {
        val fields = inferDictionary(metadatas, categoryNameIds.toMap)

        // also add redcap_event_name
        val fieldTypeSpec = inferredFieldNameTypeMap.get(visitFieldName).get.spec
        val stringEnums = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to) })
        val visitField = Field(visitFieldName, Some(visitLabel), fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnums)

        fields ++ Seq(visitField)
      }

      // save the fields
      _ <- {
        val fieldNameTypeSpecs = newFields.map( field => (field.name, field.fieldTypeSpec))
        dataSetService.updateDictionary(dataSetId, fieldNameTypeSpecs, true, true)
      }
    } yield
      newFields
  }

  private def getEnumValues(metadata: Metadata): Option[Map[Int, String]] = {
    val choices = metadata.select_choices_or_calculations.trim

    if (choices.nonEmpty) {
      try {
        val keyValueMap = choices.split(choicesDelimiter).map { choice =>
          val keyValueString = choice.split(choiceKeyValueDelimiter, 2)

          val stringKey = keyValueString(0).trim
          val value = keyValueString(1).trim

          (stringKey.toInt, value)
        }.toMap
        Some(keyValueMap)
      } catch {
        case e: NumberFormatException => throw new AdaParseException(s"RedCap Metadata '${metadata.field_name}' has non-parseable choices '${metadata.select_choices_or_calculations}'.")
      }
    } else
      None
  }

  private def getDoubles(metadata: Metadata): Option[Traversable[Double]] = {
    val choices = metadata.select_choices_or_calculations.trim

    if (choices.nonEmpty) {
      try {
        val doubles = choices.split(choicesDelimiter).map { choice =>
          val keyValueString = choice.split(choiceKeyValueDelimiter, 2)

          val stringKey = keyValueString(0).trim
          val value = keyValueString(1).trim

          stringKey.toDouble
        }
        Some(doubles)
      } catch {
        case e: NumberFormatException => throw new AdaParseException(s"RedCap Metadata '${metadata.field_name}' has non-parseable choices '${metadata.select_choices_or_calculations}'.")
      }
    } else
      None
  }
}