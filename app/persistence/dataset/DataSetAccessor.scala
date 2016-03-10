package persistence.dataset

import models.DataSetMetaInfo
import play.api.libs.json._
import persistence.RepoTypes._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

trait DataSetAccessor{
  def dataSetId: String
  def dataSetRepo: JsObjectCrudRepo
  def fieldRepo: DictionaryFieldRepo
  def categoryRepo: DictionaryCategoryRepo
  def metaInfo: Future[DataSetMetaInfo]
}

protected class DataSetAccessorImpl(
  val dataSetId: String,
  val dataSetRepo: JsObjectCrudRepo,
  val fieldRepo: DictionaryFieldRepo,
  val categoryRepo: DictionaryCategoryRepo,
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