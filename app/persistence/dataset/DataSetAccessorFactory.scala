package persistence.dataset

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.DataSetMetaInfo
import persistence.JsObjectCrudRepoFactory
import persistence.RepoTypes._
import play.api.libs.json.{Json, JsObject}
import util.RefreshableCache
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

@ImplementedBy(classOf[DataSetAccessorFactoryImpl])
trait DataSetAccessorFactory {
  def register(metaInfo: DataSetMetaInfo): Future[DataSetAccessor]
  def apply(dataSetId: String): Option[DataSetAccessor]
}

@Singleton
protected[persistence] class DataSetAccessorFactoryImpl @Inject()(
    dataSetRepoFactory: JsObjectCrudRepoFactory,
    fieldRepoFactory: DictionaryFieldRepoFactory,
    categoryRepoFactory: DictionaryCategoryRepoFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends RefreshableCache[String, DataSetAccessor] with DataSetAccessorFactory {

  override def createInstance(dataSetId: String): DataSetAccessor = {
    val collectionName = dataCollectionName(dataSetId)
    val dataSetRepo = dataSetRepoFactory(collectionName)
    val fieldRepo = fieldRepoFactory(dataSetId)
    val categoryRepo = categoryRepoFactory(dataSetId)

    new DataSetAccessorImpl(dataSetId, dataSetRepo, fieldRepo, categoryRepo, dataSetMetaInfoRepo)
  }

  override def register(metaInfo: DataSetMetaInfo) =
    dataSetMetaInfoRepo.find(Some(Json.obj("id" -> metaInfo.id))).flatMap { metaInfos =>
      val futureId = if (metaInfos.nonEmpty)
        dataSetMetaInfoRepo.update(metaInfos.head.copy(name = metaInfo.name))
      else
        dataSetMetaInfoRepo.save(metaInfo)

      futureId.map { _ =>
        cache.getOrElseUpdate(metaInfo.id, createInstance(metaInfo.id))
      }
    }

  override protected def getAllIds =
    dataSetMetaInfoRepo.find().map(_.map(_.id))

  private def dataCollectionName(dataSetId: String) = "data-" + dataSetId
}
