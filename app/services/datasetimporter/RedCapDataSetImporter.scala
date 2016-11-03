package services.datasetimporter

import java.util.Date
import javax.inject.Inject

import dataaccess.RepoTypes.DictionaryCategoryRepo
import dataaccess.RepoTypes.DictionaryFieldRepo
import dataaccess._
import models.redcap.{Metadata, FieldType => RCFieldType}
import models.{AdaParseException, AdaException, RedCapDataSetImport}
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.{RedCapServiceFactory, RedCapService}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

private class RedCapDataSetImporter @Inject() (
    redCapServiceFactory: RedCapServiceFactory
  ) extends AbstractDataSetImporter[RedCapDataSetImport] {

  private val redCapChoicesDelimiter = "\\|"
  private val redCapChoiceKeyValueDelimiter = ","
  private val redCapVisitField = "redcap_event_name"
  private val redCapVisitLabel = "Visit"

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
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo

    val stringFieldType = ftf.stringScalar

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

      // delete all the records
      _ <- {
        logger.info(s"Deleting the old data set...")
        dataRepo.deleteAll
      }

      // import dictionary (if needed) otherwise use an existing one (should exist)
      fieldNameTypeMap <-
        if (importInfo.importDictionaryFlag) {
          logger.info(s"RedCap dictionary inference and import for data set '${importInfo.dataSetId}' initiated.")

          val dictionaryMapFuture = importAndInferRedCapDictionary(redCapService, fieldRepo, categoryRepo, records)

          dictionaryMapFuture.map { dictionaryMap =>
            messageLogger.info(s"RedCap dictionary inference and import for data set '${importInfo.dataSetId}' successfully finished.")
            dictionaryMap
          }
        } else {
          logger.info(s"RedCap dictionary import disabled, using an existing dictionary.")
          fieldRepo.find().map(fields =>
            if (fields.nonEmpty) {
              val map: Map[String, FieldType[_]] = fields.map(field => (field.name, ftf(field.fieldTypeSpec): FieldType[_])).toSeq.toMap
              map
            } else {
              val message = s"No dictionary found for the data set '${importInfo.dataSetId}'. Run the REDCap data set import again with the 'import dictionary' option."
              messageLogger.error(message)
              throw new AdaException(message)
            }
          )
        }

      // get the new records
      newRecords: Traversable[JsObject] = records.map { record =>

        val newJsValues = record.fields.map { case (fieldName, jsValue) =>
          val newJsValue = fieldNameTypeMap.get(fieldName) match {
            case Some(fieldType) => try {
              fieldType.spec.fieldType match {
                case FieldTypeId.Enum => displayJsonToJsonEnum(fieldType, jsValue)
                case _ => displayJsonToJson(fieldType, jsValue)
              }
            } catch {
              case e: Exception => throw new AdaException(s"JSON value '$jsValue' of the field '$fieldName' can not be processed.", e)
            }
            // TODO: this shouldn't be like that... if we don't have a field in the dictionary, we should discard it?
            case None => jsValue
          }
          (fieldName, newJsValue)
        }

        JsObject(newJsValues)
//        val doubleFieldType = ftf(FieldTypeSpec(FieldTypeId.Double)).asInstanceOf[FieldType[Double]]
//          // TODO: replace this adhoc rounding of age with more conceptual approach
//          val ageOption = doubleFieldType.displayJsonToValue(record \ "sv_age")
//          val newAge: JsValue = ageOption.map( age =>
//            Json.toJson(age.floor)
//          ).getOrElse(JsNull)
//          record.+(("sv_age", newAge))
      }

      // save the records
      _ <- dataSetService.saveOrUpdateRecords(dataRepo, newRecords.toSeq)
    } yield
      if (importInfo.importDictionaryFlag)
        messageLogger.info(s"Import of data set and dictionary '${importInfo.dataSetName}' successfully finished.")
      else
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
  }

  protected def importAndInferRedCapDictionary(
    redCapService: RedCapService,
    fieldRepo: DictionaryFieldRepo,
    categoryRepo: DictionaryCategoryRepo,
    records: Traversable[JsObject]
  ): Future[Map[String, FieldType[_]]] = {

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
        (fieldName, fti(stringValues))
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

        def enumOrDouble: FieldTypeSpec = try {
          FieldTypeSpec(FieldTypeId.Enum, false, getEnumValues(metadata))
        } catch {
          case e: AdaParseException => {
            getDoubles(metadata)
            logger.warn(s"The field '$fieldName' has floating part(s) in the enum list and so will be treated as Double.")
            FieldTypeSpec(FieldTypeId.Double)
          }
        }

        val fieldTypeSpec = metadata.field_type match {
          case RCFieldType.radio => enumOrDouble
          case RCFieldType.checkbox => enumOrDouble
          case RCFieldType.dropdown => enumOrDouble
          case RCFieldType.calc => inferredType
          case RCFieldType.text => inferredType
          case RCFieldType.descriptive => inferredType
          case RCFieldType.yesno => FieldTypeSpec(FieldTypeId.Boolean)
          case RCFieldType.notes => inferredType
          case RCFieldType.file => inferredType
        }

        val categoryId = nameCategoryIdMap.get(metadata.form_name)
        val stringEnumValues = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to) })
        Field(metadata.field_name, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnumValues, Seq[String](), Some(metadata.field_label), categoryId)
      }.toList

    for {
      // delete all the fields
      _ <- fieldRepo.deleteAll

      // delete all the categories
      _ <- categoryRepo.deleteAll

      // obtain the RedCAP metadata
      metadatas <- redCapService.listMetadatas("field_name", "")

      // save the obtained categories and return a category name with ids
      categoryNameIds <- {
        val categories = metadatas.map(_.form_name).toSet.map { formName: String =>
          new Category(formName)
        }
        categoryRepo.save(categories).map(ids =>
          (categories, ids.toSeq).zipped.map { case (category, id) =>
            (category.name, id)
          }
        )
      }

      // fields
      newFields = {
        val fields = inferDictionary(metadatas, categoryNameIds.toMap)

        // also add redcap_event_name
        val fieldTypeSpec = inferredFieldNameTypeMap.get(redCapVisitField).get.spec
        val stringEnums = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to) })
        val visitField = Field(redCapVisitField, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnums, Nil, Some(redCapVisitLabel))

        fields ++ Seq(visitField)
      }

      // save the fields
      _ <- fieldRepo.save(newFields)
    } yield
      newFields.map(field => (field.name, ftf(field.fieldTypeSpec))).toMap
  }

  private def getEnumValues(metadata: Metadata): Option[Map[Int, String]] = {
    val choices = metadata.select_choices_or_calculations.trim

    if (choices.nonEmpty) {
      try {
        val keyValueMap = choices.split(redCapChoicesDelimiter).map { choice =>
          val keyValueString = choice.split(redCapChoiceKeyValueDelimiter, 2)

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
        val doubles = choices.split(redCapChoicesDelimiter).map { choice =>
          val keyValueString = choice.split(redCapChoiceKeyValueDelimiter, 2)

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