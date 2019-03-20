package dataaccess.mongo.dataset

import javax.cache.configuration.Factory
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import dataaccess.mongo.{ReactiveMongoApi, MongoAsyncCrudRepo}
import dataaccess.RepoTypes.DictionaryRootRepo
import models.{Category, Dictionary, DataSetFormattersAndIds}
import DataSetFormattersAndIds.{CategoryIdentity, categoryFormat, dictionaryFormat, DictionaryIdentity}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import org.incal.core.dataaccess.AsyncCrudRepo

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CategoryMongoAsyncCrudRepo @Inject()(
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[Category, BSONObjectID]("categories", dataSetId, dictionaryRepo) {

  override def save(entity: Category): Future[BSONObjectID] = {
    val initializedId = CategoryIdentity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(CategoryIdentity.set(entity, initializedId))
  }
}

class CategoryMongoAsyncCrudRepoFactory(
    dataSetId: String,
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle
  ) extends Factory[AsyncCrudRepo[Category, BSONObjectID]] {

  override def create(): AsyncCrudRepo[Category, BSONObjectID] = {
    val dictionaryRepo = new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = ReactiveMongoApi.create(configuration, applicationLifecycle)

    val repo = new CategoryMongoAsyncCrudRepo(dataSetId, dictionaryRepo)
    Await.result(repo.initIfNeeded, 30 seconds)
    repo
  }
}