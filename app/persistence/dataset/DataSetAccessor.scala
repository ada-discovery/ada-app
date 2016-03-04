package persistence.dataset

import java.util.concurrent.locks.Lock
import javax.inject.{Singleton, Inject}

import _root_.util.RefreshableCache
import com.google.inject.ImplementedBy
import models.DataSetMetaInfo
import persistence.JsObjectMongoCrudRepo
import collection.mutable.{Map => MMap}
import play.api.libs.json._
import persistence.RepoTypes._
import play.modules.reactivemongo.ReactiveMongoApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait DataSetAccessor{
  def dataSetId: String
  def dataSetRepo: JsObjectCrudRepo
  def dictionaryFieldRepo: DictionaryFieldRepo

  def metaInfo: Future[DataSetMetaInfo]
}

case class DataSetAccessorImpl(
  val dataSetId: String,
  val dataSetRepo: JsObjectCrudRepo,
  val dictionaryFieldRepo: DictionaryFieldRepo,
  dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DataSetAccessor{

  override def metaInfo = {
    val metaInfosFuture = dataSetMetaInfoRepo.find(Some(Json.obj("id" -> dataSetId)))
    metaInfosFuture.map { metaInfos =>
      if (metaInfos.nonEmpty)
        metaInfos.head
      else
        throw new IllegalAccessError("Meta info not available for data set '" + dataSetId + "'.")
    }
  }
}

