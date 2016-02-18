package runnables.luxpark

import javax.inject.{Named, Inject}

import models.redcap.Metadata
import models.{FieldType, Field, Category}
import models.redcap.{FieldType => RCFieldType}
import persistence.{DictionaryFieldRepo, RepoSynchronizer}
import persistence.RepoTypeRegistry._
import play.api.libs.json.Json
import runnables.{GuiceBuilderRunnable, InferDictionary}
import services.{DeNoPaSetting, RedCapService}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonUtil

import scala.concurrent.duration._

import scala.concurrent.{Future, Await}

class ImportLuxParkDictionaryFromRedCap @Inject() (
    @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo,
    redCapService: RedCapService
  ) extends InferDictionary(dictionaryRepo) {

  // TODO: Introduce a proper type inference setting for LuxPark Data
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("cdisc_dm_usubjd" -> "ND0001")

  private val choicesDelimeter = "\\|"
  private val choiceKeyValueDelimeter = ","

  override def run = {
    val dictionarySyncRepo = RepoSynchronizer(dictionaryRepo, timeout)
    // init dictionary if needed
    Await.result(dictionaryRepo.initIfNeeded, timeout)
    dictionarySyncRepo.deleteAll

    val metadataFuture = redCapService.listMetadatas("field_name", "")
    val metadatas = Await.result(metadataFuture, timeout)

    // categories
    val categories = metadatas.map(_.form_name).toSet.map { formName : String =>
      new Category(formName)
    }.toList
    val nameCategoryMap = categories.map(category => (category.name, category)).toMap
    val rootCategory = new Category("")
    rootCategory.addChildren(categories)


    val fieldNames = getFieldNames

    val futures = metadatas.par.map{ metadata =>
      val fieldName = metadata.field_name
      if (fieldNames.contains(fieldName)) {
        println(fieldName + " " + metadata.field_type)
        val inferredType = Await.result(inferType(fieldName), timeout)

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

        val category = nameCategoryMap.get(metadata.form_name).get
        val field = Field(metadata.field_name, fieldType, false, numValues, Seq[String](), Some(metadata.field_label))
        dictionaryRepo.save(field)
      } else
        Future(Unit)
    }
    // to be safe, wait for each save call to finish
    futures.toList.foreach(future => Await.result(future, timeout))
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
