package persistence

import models.{Dictionary, Field}
import persistence.RepoTypeRegistry.{FieldRepo, JsObjectCrudRepo}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.json.BSONFormats._
import models.Dictionary.DictionaryFormat

trait DictionaryRepo {

  def dataRepo : JsObjectCrudRepo
//  def fieldRepo : JsObjectCrudRepo

  def get : Future[Dictionary]
  def initIfNeeded : Future[Boolean]
}

protected class DictionaryMongoAsyncCrudRepo(
    private val dataSetName : String,
    private val _dataSetRepo : JsObjectCrudRepo
  ) extends MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries") with DictionaryRepo {

  override def dataRepo = _dataSetRepo

  override def get = {
    getByDataSetName.map(dictionaries =>
      if (dictionaries.isEmpty)
        throw new IllegalArgumentException("Dictionary was not initialized")
      else
        dictionaries.head
    )
  }

  override def initIfNeeded = synchronized {
    getByDataSetName.map(dictionaries =>
      if (dictionaries.isEmpty) {
        save(Dictionary(None, dataSetName, List[Field]()))
        true
      } else
        false
    )
  }

  private def getByDataSetName =
    find(Some(Json.obj("dataSetName" -> dataSetName)))
}

//protected class FieldMongoAsyncCrudRepo extends MongoAsyncCrudRepo[Field, BSONObjectID] with FieldRepo {
//
//}