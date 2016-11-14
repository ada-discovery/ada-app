package dataaccess.mongo.dataset

import javax.cache.configuration.Factory
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import dataaccess.mongo.{ReactiveMongoApi, MongoAsyncCrudRepo}
import dataaccess.AsyncCrudRepo
import dataaccess.RepoTypes.DictionaryRootRepo
import models.{Filter, Dictionary}
import models.DataSetFormattersAndIds.{DictionaryIdentity, dictionaryFormat}
import models.FilterCondition.{FilterIdentity, filterFormat}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

class FilterMongoAsyncCrudRepo @Inject()(
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[Filter, BSONObjectID]("filters", dataSetId, dictionaryRepo) {

  override def save(entity: Filter): Future[BSONObjectID] = {
    val initializedId = FilterIdentity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(FilterIdentity.set(entity, initializedId))
  }
}

class FilterMongoAsyncCrudRepoFactory(
    dataSetId: String,
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle
  ) extends Factory[AsyncCrudRepo[Filter, BSONObjectID]] {

  override def create(): AsyncCrudRepo[Filter, BSONObjectID] = {
    val dictionaryRepo = new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = ReactiveMongoApi.create(configuration, applicationLifecycle)

    val repo = new FilterMongoAsyncCrudRepo(dataSetId, dictionaryRepo)
    Await.result(repo.initIfNeeded, 10 seconds)
    repo
  }
}