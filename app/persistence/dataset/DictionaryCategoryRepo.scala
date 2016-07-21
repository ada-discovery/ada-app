package persistence.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import models.DataSetFormattersAndIds.{categoryFormat, CategoryIdentity => identity}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import models.Category
import persistence.RepoTypes._
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

trait DictionaryCategoryRepoFactory {
  def apply(dataSetId: String): DictionaryCategoryRepo
}

protected[persistence] class DictionaryCategorySubordinateMongoAsyncCrudRepo @Inject()(
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[Category, BSONObjectID]("categories", dataSetId, dictionaryRepo) {

  override def save(entity: Category): Future[BSONObjectID] = {
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}

object DictionaryCategoryRepo {

  def saveRecursively(
    categoryRepo: DictionaryCategoryRepo,
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