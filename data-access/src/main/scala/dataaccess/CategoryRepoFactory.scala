package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.CategoryRepo
import dataaccess.ignite.CategoryCacheCrudRepoFactory
import models.Category
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

@ImplementedBy(classOf[CategoryCacheCrudRepoFactory])
trait CategoryRepoFactory {
  def apply(dataSetId: String): CategoryRepo
}

object CategoryRepo {

  def saveRecursively(
    categoryRepo: CategoryRepo,
    category: Category
  ): Future[Seq[(Category, BSONObjectID)]] = {
    val children = category.children
    category.children = Nil
    categoryRepo.save(category).flatMap { id =>
      val idsFuture = children.map { child =>
        child.parentId = Some(id)
        saveRecursively(categoryRepo, child)
      }
      Future.sequence(idsFuture).map(ids => Seq((category, id)) ++ ids.flatten)
    }
  }
}