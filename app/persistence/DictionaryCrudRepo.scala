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

trait DictionaryFieldRepo extends AsyncCrudRepo[Field, String] {

  def dataRepo : JsObjectCrudRepo
  def get : Future[Dictionary]
  def initIfNeeded : Future[Boolean]
}

protected class DictionaryFieldMongoAsyncCrudRepo(
    private val dataSetName : String,
    private val _dataSetRepo : JsObjectCrudRepo
  ) extends DictionaryFieldRepo {

  @Inject var dictionaryRepo: DictionaryRootRepo = _

  override def dataRepo = _dataSetRepo

  override def get: Future[Dictionary] = {
    getByDataSetName.map(dictionaries =>
      if (dictionaries.isEmpty)
        throw new IllegalArgumentException("Dictionary was not initialized")
      else
        dictionaries.head
    )
  }

  override def initIfNeeded: Future[Boolean] = synchronized {
    val responseFuture = getByDataSetName.map(dictionaries =>
      if (dictionaries.isEmpty) {
        dictionaryRepo.save(Dictionary(None, dataSetName, List[Field]()))
        true
      } else
        false
    )

    // init dictionary id: TODO: move after dictionaryrepo injection
    responseFuture.map { response =>
      dictionaryId;
      response
    }
  }

  private def getByDataSetName: Future[Traversable[Dictionary]] =
    dictionaryRepo.find(Some(Json.obj("dataSetName" -> dataSetName)))

  private lazy val dictionaryId: BSONObjectID = synchronized {
    val futureId = dictionaryRepo.find(
      Some(Json.obj("dataSetName" -> dataSetName)), None, None
    ).map(_.head._id.get)
    Await.result(futureId, 120000 millis)
  }


  /**
    * TODO: implement
    * Update single Field in repo.
    * The properties of the passed field replace the properties of the field in the repo.
    *
    * @param entity Field to be updated. entity.name must match an existing Field.name.
    * @return Either object with Right indicating the success or failure.
    */
  override def update(entity: Field): Future[Either[String, String]] = ???


  /**
    * TODO: implement
    *
    *
    * @param id
    * @param modifier
    * @return
    */
  override def updateCustom(id: String, modifier: JsObject): Future[Either[String, String]] = ???


  /**
    * Iterate over all elements in repo and reset them.
    * @see update()
    *
    * @return String indicating success or failure ("success", "fail").
    */
  override def deleteAll: Future[String] =
    get.flatMap { dictionary =>
      dictionaryRepo.update(dictionary.copy(fields = List[Field]())).map {
        case Right(id) => "success"
        case Left(err) => "fail"
      }
    }


  /**
    * Delete single entry identified by its id (name).
    *
    * @param id Name of the field to be deleted.
    * @return Either object with Right indicating the success or failure.
    */
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


  /**
    * Converts the given Field into Json format and calls updateCustom() to update/ save it in the repo.
    * @see updateCustom()
    *
    * @param entity Field to be updated/ saved
    * @return Either object with Right indicating the success or failure.
    */
  override def save(entity: Field): Future[Either[String, String]] = {
    val modifier = Json.obj {
      "$push" -> Json.obj {
        "fields" -> Json.toJson(entity)
      }
    }
    dictionaryRepo.updateCustom(dictionaryId, modifier) map {
      case Right(id) => Right("success")
      case Left(err) => Right("fail")
    }
  }


  /**
    * Counts all items in repo matching criteria.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @return Number of maching elements.
    */
  override def count(criteria: Option[JsObject]): Future[Int] = {
    var futureResult: Future[Traversable[Field]] = find(criteria, None, None, None, None)
    futureResult.map(t => t.size)
  }

  /**
    * Retrieve field(s) from the repo.
    *
    * @param name Name of object.
    * @return Fields in the dicitonary with exact name match.
    */
  override def get(name: String): Future[Option[Field]] =
    get.map(dictionary => dictionary.fields.find(_.name.equals(name)))

  /**
    * TODO: use pagination and projection parameters or remove them.
    *
    * Find object matching the filtering criteria. Fields may be ordered and only a subset of them used.
    * Pagination options for page limit and page number are available to limit number of returned results.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @param orderBy Column used as reference for sorting. Leave at None to use default.
    * @param projection Defines which columns are supposed to be returned. Leave at None to use default.
    * @param limit Page limit. Use to define chunk sizes for pagination. Leave at None to use default.
    * @param page Page to be returned. Specifies which chunk is returned.
    * @return Traversable object for iteration.
    */
  override def find(
     criteria: Option[JsObject],
     orderBy: Option[JsObject],
     projection: Option[JsObject],
     limit: Option[Int],
     page: Option[Int]
  ): Future[Traversable[Field]] = {
    criteria match {
      case None => get.map(dictionary => dictionary.fields)
      case _ => get.map(dictionary =>
        // start filtering
        // use slice for pagination



        dictionary.fields
      )
    }

    //get.map(dictionary => dictionary.fields)
    //get.map(dictionary => dictionary.fields.map(field =>
  }


  /**
    * Return all field names.
    *
    * @return Field names.
    */
  def getFieldNames: Future[Traversable[String]] = {
    get.map(dictionary =>
      dictionary.fields.map(field =>
        field.name
      )
    )
  }

}
