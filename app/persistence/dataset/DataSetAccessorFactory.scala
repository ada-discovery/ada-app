package persistence.dataset

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.DataSetMetaInfo
import persistence.JsObjectMongoCrudRepo
import persistence.RepoTypes._
import play.modules.reactivemongo.ReactiveMongoApi
import util.RefreshableCache
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

@ImplementedBy(classOf[DataSetAccessorMongoFactory])
trait DataSetAccessorFactory {
  def register(metaInfo: DataSetMetaInfo): Future[DataSetAccessor]
  def apply(dataSetId: String): Option[DataSetAccessor]
}

@Singleton
protected[persistence] class DataSetAccessorMongoFactory @Inject()(
    reactiveMongoApi : ReactiveMongoApi,
    dictionaryRepo: DictionaryRootRepo,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends RefreshableCache[String, DataSetAccessor] with DataSetAccessorFactory {

  override def createInstance(dataSetId: String): DataSetAccessor = {
    val collectionName = dataCollectionName(dataSetId)
    val dataSetRepo = crateDataSetRepo(collectionName)
    val dictionaryFieldRepo = createDictionaryRepo(dataSetId)
    DataSetAccessorImpl(dataSetId, dataSetRepo, dictionaryFieldRepo, dataSetMetaInfoRepo)
  }

  // TODO: call update
  override def register(metaInfo: DataSetMetaInfo) =
//    dataSetMetaInfoRepo.update(metaInfo).map(_ => Unit)
    dataSetMetaInfoRepo.save(metaInfo).map { _ =>
      val accessor = createInstance(metaInfo.id)
      cache.put(metaInfo.id, accessor)
      accessor
    }

  override protected def getAllIds =
    dataSetMetaInfoRepo.find().map(_.map(_.id))

  // TODO: replace with a Guice assist inject factory
  private def crateDataSetRepo(dataCollectionName : String) = {
    val repo = new JsObjectMongoCrudRepo(dataCollectionName)
    repo.reactiveMongoApi = reactiveMongoApi
    repo
  }

  // TODO: replace with a Guice assist inject factory
  private def createDictionaryRepo(dataSetId : String) = {
    val repo = new DictionaryFieldMongoAsyncCrudRepo(dataSetId)
    repo.dictionaryRepo = dictionaryRepo
    repo
  }

  private def dataCollectionName(dataSetId: String) = "data-" + dataSetId
}
