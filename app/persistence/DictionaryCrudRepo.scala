package persistence

import javax.inject.Inject

import models.{Dictionary, Field}
import persistence.RepoTypeRegistry.{DictionaryRootRepo, JsObjectCrudRepo}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.bson.BSONObjectID
import scala.concurrent.{Await, Future}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.json.BSONFormats._
import models.Dictionary._
import scala.concurrent.duration._

trait DictionaryRepo extends AsyncCrudRepo[Field, String] {

  def dataRepo : JsObjectCrudRepo
  def get : Future[Dictionary]
  def initIfNeeded : Future[Boolean]
}

protected class DictionaryMongoAsyncCrudRepo(
    private val dataSetName : String,
    private val _dataSetRepo : JsObjectCrudRepo
  ) extends DictionaryRepo {

  @Inject var dictionaryRepo : DictionaryRootRepo = _

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
    val responseFuture = getByDataSetName.map(dictionaries =>
      if (dictionaries.isEmpty) {
        dictionaryRepo.save(Dictionary(None, dataSetName, List[Field]()))
        true
      } else
        false
    )

    // init dictionary id: TODO: move after dictionaryrepo injection
    responseFuture.map{response =>
      dictionaryId;
      response
    }
  }

  private def getByDataSetName =
    dictionaryRepo.find(Some(Json.obj("dataSetName" -> dataSetName)))

  private lazy val dictionaryId = synchronized {
    val futureId = dictionaryRepo.find(
      Some(Json.obj("dataSetName" -> dataSetName)), None, None
    ).map(_.head._id.get)
    Await.result(futureId, 120000 millis)
  }

  // TODO
  override def update(entity: Field): Future[Either[String, String]] = ???

  // TODO
  override def updateCustom(id: String, modifier: JsObject): Future[Either[String, String]] = ???

  override def deleteAll: Future[String] =
    get.flatMap { dictionary =>
      dictionaryRepo.update(dictionary.copy(fields = List[Field]())).map {
        case Right(id) => "success"
        case Left(err) => "fail"
      }
    }

  override def delete(id: String): Future[Either[String, String]] =
    get.flatMap { dictionary =>
      val field = Field(id)
      val newFields = dictionary.fields.filterNot(_.equals(field))
      if (dictionary.fields.size == newFields.size) {
        Future(Left("id"))
      } else {
        dictionaryRepo.update(dictionary.copy(fields = newFields)).map {
          case Right(id) => Right("success")
          case Left(err) => Right("fail")
        }
      }
    }

  override def save(entity: Field): Future[Either[String, String]] = {
    val modifier = Json.obj {
      "$push" -> Json.obj{ "fields" -> Json.toJson(entity) }
    }
    dictionaryRepo.updateCustom(dictionaryId, modifier) map {
      case Right(id) => Right("success")
      case Left(err) => Right("fail")
    }
  }

  // TODO
  override def count(criteria: Option[JsObject]): Future[Int] = ???

  override def get(name: String): Future[Option[Field]] =
    get.map(dictionary => dictionary.fields.find(_.name.equals(name)))

  override def find(
    criteria: Option[JsObject],
    orderBy: Option[JsObject],
    projection: Option[JsObject],
    limit: Option[Int],
    page: Option[Int]
  ): Future[Traversable[Field]] =
    // TODO
    get.map(dictionary => dictionary.fields)
}