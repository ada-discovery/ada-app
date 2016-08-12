package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.DictionaryCategoryRepo
import dataaccess.ignite.DictionaryCategoryCacheCrudRepoFactory
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

@ImplementedBy(classOf[DictionaryCategoryCacheCrudRepoFactory])
trait DictionaryCategoryRepoFactory {
  def apply(dataSetId: String): DictionaryCategoryRepo
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