package dataaccess.mongo.dataset

import javax.cache.configuration.Factory
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import models.{Field, Dictionary, DataSetFormattersAndIds}
import DataSetFormattersAndIds._
import dataaccess.mongo.{ReactiveMongoApi, MongoAsyncCrudRepo}
import dataaccess.AsyncCrudRepo
import dataaccess.RepoTypes.DictionaryRootRepo
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import scala.concurrent.duration._

import scala.concurrent.Await

class DictionaryFieldMongoAsyncCrudRepo @Inject()(
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[Field, String]("fields", dataSetId, dictionaryRepo)

class DictionaryFieldMongoAsyncCrudRepoFactory(
    dataSetId: String,
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle
  ) extends Factory[AsyncCrudRepo[Field, String]] {

  override def create(): AsyncCrudRepo[Field, String] = {
    val dictionaryRepo = new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = ReactiveMongoApi.create(configuration, applicationLifecycle)

    val repo = new DictionaryFieldMongoAsyncCrudRepo(dataSetId, dictionaryRepo)
    Await.result(repo.initIfNeeded, 10 seconds)
    repo
  }
}

