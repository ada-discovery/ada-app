package dataaccess.mongo.dataset

import javax.cache.configuration.Factory
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import dataaccess.mongo.{ReactiveMongoApi, MongoAsyncCrudRepo}
import dataaccess.{Dictionary, AsyncCrudRepo, Category}
import dataaccess.RepoTypes.DictionaryRootRepo
import dataaccess.DataSetFormattersAndIds.{CategoryIdentity, categoryFormat, dictionaryFormat, DictionaryIdentity}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

class DictionaryCategoryMongoAsyncCrudRepo @Inject()(
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[Category, BSONObjectID]("categories", dataSetId, dictionaryRepo) {

  override def save(entity: Category): Future[BSONObjectID] = {
    val initializedId = CategoryIdentity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(CategoryIdentity.set(entity, initializedId))
  }
}

class DictionaryCategoryMongoAsyncCrudRepoFactory(
    dataSetId: String,
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle
  ) extends Factory[AsyncCrudRepo[Category, BSONObjectID]] {

  override def create(): AsyncCrudRepo[Category, BSONObjectID] = {
    val dictionaryRepo = new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = ReactiveMongoApi.create(configuration, applicationLifecycle)

    val repo = new DictionaryCategoryMongoAsyncCrudRepo(dataSetId, dictionaryRepo)
    Await.result(repo.initIfNeeded, 10 seconds)
    repo
  }
}