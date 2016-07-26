package persistence.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import models.DataSetFormattersAndIds._
import models.Field
import models.Criterion.CriterionInfix
import persistence.RepoTypes._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import scala.concurrent.Future
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

trait DictionaryFieldRepoFactory {
  def apply(dataSetId: String): DictionaryFieldRepo
}

protected[persistence] class DictionaryFieldSubordinateMongoAsyncCrudRepo @Inject()(
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[Field, String]("fields", dataSetId, dictionaryRepo)

object DictionaryFieldRepo {

  def setCategoriesById(
    categoryRepo: DictionaryCategoryRepo,
    fields: Traversable[Field]
  ): Future[Unit] = {
    val categoryIds = fields.map(_.categoryId).flatten.toSeq

    categoryRepo.find(Seq(CategoryIdentity.name #=> categoryIds)).map { categories =>
      val categoryIdMap = categories.map( c => (c._id.get, c)).toMap
      fields.foreach( field =>
        if (field.categoryId.isDefined) {
          field.category = categoryIdMap.get(field.categoryId.get)
        }
      )
    }
  }

  def setCategoryById(
    categoryRepo: DictionaryCategoryRepo,
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
    categoryRepo: DictionaryCategoryRepo,
    field: Field
  ): Future[Unit] =
    field.category match {
      case Some(category) =>
        categoryRepo.find(Seq("name" #= category.name)).map(categories =>
          if (categories.nonEmpty) {
            val loadedCategory = categories.head
            field.category = Some(loadedCategory)
            field.categoryId = loadedCategory._id
          }
        )
      case None => Future(())
    }
}