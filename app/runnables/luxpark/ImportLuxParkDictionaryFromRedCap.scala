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
    val categories = metadatas.map{ metadata =>
      (metadata \ "form_name").as[String]
    }.toSet.map { name : String => new Category(name) }.toList

    rootCategory.addChildren(categories)
    val nameCategoryMap = categories.map(category => (category.name, category)).toMap

    // field category map
    val fieldCategoryMap = metadatas.map{ metadata =>
      val field = (metadata \ "field_name").as[String]
      val categoryName = (metadata \ "form_name").as[String]
      (field, nameCategoryMap.get(categoryName).get)
    }.toMap

    // field label map
    val fieldLabelMap = metadatas.map{metadata =>
      val field = (metadata \ "field_name").as[String]
      val label = (metadata \ "field_label").as[String]
      (field, label)
    }.toMap
  }
}
