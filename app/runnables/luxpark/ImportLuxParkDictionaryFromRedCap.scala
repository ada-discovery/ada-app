package runnables.luxpark

import javax.inject.Inject

import models.redcap.Metadata
import models.{FieldType, Field, Category}
import models.redcap.{FieldType => RCFieldType}
import persistence.RepoSynchronizer
import models.DataSetId._
import play.api.libs.json.Json
import runnables.{GuiceBuilderRunnable, InferDictionary}
import services.{DeNoPaSetting, RedCapService}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonUtil
import scala.concurrent.{Await, Future}
import scala.concurrent.Await.result

class ImportLuxParkDictionaryFromRedCap @Inject() (
    redCapService: RedCapService
  ) extends InferDictionary(luxpark) {

  // TODO: Introduce a proper type inference setting for LuxPark Data
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("cdisc_dm_usubjd" -> "ND0001")

  private val choicesDelimeter = "\\|"
  private val choiceKeyValueDelimeter = ","

  override def run = {
    val dictionarySyncRepo = RepoSynchronizer(fieldRepo, timeout)
    // init dictionary if needed
    result(fieldRepo.initIfNeeded, timeout)
    // delete all fields
    dictionarySyncRepo.deleteAll
    // delete all categories
    result(categoryRepo.deleteAll, timeout)

    val metadataFuture = redCapService.listMetadatas("field_name", "")
    val metadatas = result(metadataFuture, timeout)

    // categories
    val nameCategoryIdFutures = metadatas.map(_.form_name).toSet.map { formName : String =>
      val category = new Category(formName)
      categoryRepo.save(category).map(id => (category.name, id))
    }

    val nameCategoryIdMap = result(Future.sequence(nameCategoryIdFutures), timeout).toMap

    val fieldNames = getFieldNames

    val futures = metadatas.par.map{ metadata =>
      val fieldName = metadata.field_name
      if (fieldNames.contains(fieldName)) {
        println(fieldName + " " + metadata.field_type)
        val inferredType = result(inferType(fieldName), timeout)

        val (fieldType, numValues) = metadata.field_type match {
          case RCFieldType.radio => (FieldType.Enum, getEnumValues(metadata))
          case RCFieldType.checkbox => (FieldType.Enum, getEnumValues(metadata))
          case RCFieldType.dropdown => (FieldType.Enum, getEnumValues(metadata))

          case RCFieldType.calc => (inferredType, None)
          case RCFieldType.text => (inferredType, None)
          case RCFieldType.descriptive => (inferredType, None)
          case RCFieldType.yesno => (inferredType, None)
          case RCFieldType.notes => (inferredType, None)
          case RCFieldType.file => (inferredType, None)
        }

        val categoryId = nameCategoryIdMap.get(metadata.form_name)
        val field = Field(metadata.field_name, fieldType, false, numValues, Seq[String](), Some(metadata.field_label), categoryId)
        fieldRepo.save(field)
      } else
        Future(Unit)
    }

    // to be safe, wait for each save call to finish
    futures.toList.foreach(result(_, timeout))
  }

  private def getEnumValues(metadata: Metadata) : Option[Map[String, String]] = {
    val choices = metadata.select_choices_or_calculations.trim

    if (choices.nonEmpty) {
      try {
        val keyValueMap = choices.split(choicesDelimeter).map { choice =>
          val keyValueString = choice.split(choiceKeyValueDelimeter, 2)
          (JsonUtil.escapeKey(keyValueString(0).trim), keyValueString(1).trim)
        }.toMap
        Some(keyValueMap)
      } catch {
        case e: Exception => throw new IllegalArgumentException(s"RedCap Metadata '${metadata.field_name}' has non-parseable choices '${metadata.select_choices_or_calculations}'.")
      }
    } else
      None
  }
}

object ImportLuxParkDictionaryFromRedCap extends GuiceBuilderRunnable[ImportLuxParkDictionaryFromRedCap] with App { run }
