package persistence.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import models.DataSetFormattersAndIds.{categoryFormat, CategoryIdentity => identity}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import models.Category
import persistence.RepoTypes._
import play.modules.reactivemongo.json.BSONObjectIDFormat
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

trait DictionaryCategoryRepoFactory {
  def apply(dataSetId: String): DictionaryCategoryRepo
}

protected[persistence] class DictionaryCategoryMongoAsyncCrudRepo @Inject() (
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[Category, BSONObjectID]("categories", dataSetId, dictionaryRepo) with DictionaryCategoryRepo {

  override def save(entity: Category): Future[BSONObjectID] = {
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}

object DictionaryCategoryRepo {

  def saveRecursively(
    categoryRepo: CategoryRepo,
    category: Category
  ): Future[Seq[(Category, BSONObjectID)]] =
    categoryRepo.save(category).flatMap { id =>
      val idsFuture = category.children.map { child =>
        child.parentId = Some(id)
        saveRecursively(categoryRepo, child)
      }
      Future.sequence(idsFuture).map(ids => Seq((category, id)) ++ ids.flatten)
    }
}