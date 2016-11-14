package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.Criterion.CriterionInfix
import models.{Field, DataSetFormattersAndIds}
import DataSetFormattersAndIds.CategoryIdentity
import dataaccess.RepoTypes._
import dataaccess.ignite.FieldCacheCrudRepoFactory
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

@ImplementedBy(classOf[FieldCacheCrudRepoFactory])
trait FieldRepoFactory {
  def apply(dataSetId: String): FieldRepo
}

object DictionaryFieldRepo {

  def setCategoriesById(
    categoryRepo: CategoryRepo,
    fields: Traversable[Field]
  ): Future[Unit] = {
    val categoryIds = fields.map(_.categoryId).flatten.map(Some(_)).toSeq

    if (categoryIds.nonEmpty) {
      categoryRepo.find(Seq(CategoryIdentity.name #=> categoryIds)).map { categories =>
        val categoryIdMap = categories.map(c => (c._id.get, c)).toMap
        fields.foreach(field =>
          if (field.categoryId.isDefined) {
            field.category = categoryIdMap.get(field.categoryId.get)
          }
        )
      }
    } else
      Future(())
  }

  def setCategoryById(
                       categoryRepo: CategoryRepo,
                       field: Field
  ): Future[Unit] =
    field.categoryId match {
      case Some(categoryId) =>
        categoryRepo.get(categoryId).map{ category =>
          field.category = category
        }
      case None => Future(())
    }

  // search for a category with a given name (if multiple, select the first one)
  def setCategoryByName(
                         categoryRepo: CategoryRepo,
                         field: Field
  ): Future[Unit] =
    field.category match {
      case Some(category) =>
        categoryRepo.find(Seq("name" #== category.name)).map(categories =>
          if (categories.nonEmpty) {
            val loadedCategory = categories.head
            field.category = Some(loadedCategory)
            field.categoryId = loadedCategory._id
          }
        )
      case None => Future(())
    }
}