package runnables.luxpark

import javax.inject.{Named, Inject}

import models.Category
import persistence.RepoSynchronizer
import persistence.RepoTypeRegistry._
import services.RedCapService

import scala.concurrent.duration._

import scala.concurrent.Await

class ImportLuxParkDictionaryFromRedCap @Inject() (
    @Named("LuxParkRepo") dataRepo: JsObjectCrudRepo,
    redCapService: RedCapService
  ) extends Runnable {

  private val timeout = 120000 millis
  private val syncDataRepo = RepoSynchronizer(dataRepo, timeout)

  def run = {
    val metadataFuture = redCapService.listMetadatas("field_name", "")
    val metadatas = Await.result(metadataFuture, timeout)

    // categories
    val rootCategory = new Category("")
    val categories = metadatas.map(_.form_name).toSet.map { formName : String =>
      new Category(formName)
    }.toList

    rootCategory.addChildren(categories)
    val nameCategoryMap = categories.map(category => (category.name, category)).toMap

    // field category map
    val fieldCategoryMap = metadatas.map{metadata =>
      (metadata.field_name, nameCategoryMap.get(metadata.form_name).get)
    }.toMap

    // field label map
    val fieldLabelMap = metadatas.map{metadata =>
      (metadata.field_name, metadata.field_label)
    }.toMap
  }
}
