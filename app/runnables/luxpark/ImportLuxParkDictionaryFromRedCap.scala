package runnables.luxpark

import javax.inject.{Named, Inject}

import models.{Field, Category}
import persistence.{DictionaryFieldRepo, RepoSynchronizer}
import persistence.RepoTypeRegistry._
import play.api.libs.json.Json
import runnables.{GuiceBuilderRunnable, InferDictionary}
import services.{DeNoPaSetting, RedCapService}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.duration._

import scala.concurrent.{Future, Await}

class ImportLuxParkDictionaryFromRedCap @Inject() (
    @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo,
    redCapService: RedCapService
  ) extends InferDictionary(dictionaryRepo) {

  // TODO: Introduce a proper type inference setting for LuxPark Data
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("cdisc_dm_usubjd" -> "ND0001")

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
        println(fieldName)
        val inferredType = Await.result(inferType(fieldName), timeout)

        val fieldType = metadata.field_type match {
          case radio => inferredType
          case calc => inferredType
          case text => inferredType
          case checkbox => inferredType
          case descriptive => inferredType
          case yesno => inferredType
          case dropdown => inferredType
          case notes => inferredType
          case file => inferredType
        }

        val category = nameCategoryMap.get(metadata.form_name).get
        val field = Field(metadata.field_name, fieldType, false, None, Seq[String](), Some(metadata.field_label))
        dictionaryRepo.save(field)
      } else
        Future(Unit)
    }
    // to be safe, wait for each save call to finish
    futures.toList.foreach(future => Await.result(future, timeout))
  }
}

object ImportLuxParkDictionaryFromRedCap extends GuiceBuilderRunnable[ImportLuxParkDictionaryFromRedCap] with App { run }
