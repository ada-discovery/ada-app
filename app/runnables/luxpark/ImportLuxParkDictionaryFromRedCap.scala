package runnables.luxpark

import javax.inject.Inject

import models.redcap.Metadata
import models.{FieldType, Field, Category}
import models.redcap.{FieldType => RCFieldType}
import persistence.RepoSynchronizer
import runnables.DataSetId.luxpark
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import runnables.GuiceBuilderRunnable
import services.{DataSetService, DeNoPaSetting, RedCapService}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.JsonUtil
import scala.collection.Set
import scala.concurrent.{Await, Future}
import scala.concurrent.Await.result
import scala.concurrent.duration._

class ImportLuxParkDictionaryFromRedCap @Inject() (
    dsaf: DataSetAccessorFactory,
    redCapService: RedCapService,
    dataSetService: DataSetService
  ) extends Runnable {

  private val choicesDelimeter = "\\|"
  private val choiceKeyValueDelimeter = ","
  private val timeout = 120000 millis
  private val visitField = "redcap_event_name"

  override def run = {
    val dsa = dsaf(luxpark).get
    val dataSetRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo

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

    val fieldNames = result(getFieldNames(dataSetRepo), timeout)

    val futures = metadatas.par.map{ metadata =>
      val fieldName = metadata.field_name
      if (fieldNames.contains(fieldName)) {
        println(fieldName + " " + metadata.field_type)
        val fieldTypeFuture = dataSetService.inferFieldType(dataSetRepo, DeNoPaSetting.typeInferenceProvider, fieldName)
        val (isArray, inferredType) = result(fieldTypeFuture, timeout)

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
        val field = Field(metadata.field_name, fieldType, isArray, numValues, Seq[String](), Some(metadata.field_label), categoryId)
        fieldRepo.save(field)
      } else
        Future(Unit)
    }

    // also add redcap_event_name
    val visitFieldFuture = fieldRepo.save(Field(visitField, FieldType.Enum))

    // to be safe, wait for each save call to finish
    result(Future.sequence(futures.toList ++ Seq(visitFieldFuture)), timeout)
  }

  private def getFieldNames(dataRepo: JsObjectCrudRepo): Future[Set[String]] =
    for {
      records <- dataRepo.find(None, None, None, Some(1))
    } yield
      records.headOption.map(_.keys).getOrElse(
        throw new IllegalStateException(s"No records found. Unable to obtain field names. The associated data set might be empty.")
      )

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