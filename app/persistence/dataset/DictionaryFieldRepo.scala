package persistence.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import models.DataSetFormattersAndIds._
import models.Field
import persistence.RepoTypes.{DictionaryFieldRepo, CategoryRepo, DictionaryRootRepo}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import scala.concurrent.Future

trait DictionaryFieldRepoFactory {
  def apply(dataSetId: String): DictionaryFieldRepo
}

protected[persistence] class DictionaryFieldMongoAsyncCrudRepo @Inject() (
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[Field, String]("fields", dataSetId, dictionaryRepo) with DictionaryFieldRepo

object DictionaryFieldRepo {

  def setCategoriesById(
    categoryRepo: CategoryRepo,
    fields: Traversable[Field]
  ): Future[Unit] = {
    val futureUnits = fields.map(setCategoryById(categoryRepo, _))
    Future.sequence(futureUnits).map(_ => ())
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
        categoryRepo.find(Some(Json.obj("name" -> category.name))).map(categories =>
          if (categories.nonEmpty) {
            val loadedCategory = categories.head
            field.category = Some(loadedCategory)
            field.categoryId = loadedCategory._id
          }
        )
      case None => Future(())
    }
}