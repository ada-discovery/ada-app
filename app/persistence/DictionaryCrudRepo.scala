package persistence

import javax.inject.Inject

import models.{Dictionary, Field}
import persistence.RepoTypeRegistry.{DictionaryRootRepo, JsObjectCrudRepo}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.bson.BSONObjectID
import scala.concurrent.{Await, Future}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
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
        throw new IllegalAccessException("Dictionary was not initialized")
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
  override def update(entity: Field): Future[String] = ???


  /**
    * TODO: implement
    *
    *
    * @param id
    * @param modifier
    * @return
    */
  override def updateCustom(id: String, modifier: JsObject): Future[String] = ???


  /**
    * Iterate over all elements in repo and reset them.
    * @see update()
    *
    * @return String indicating success or failure ("success", "fail").
    */
  override def deleteAll: Future[String] = {
    get.flatMap { dictionary =>
      dictionaryRepo.update(dictionary.copy(fields = List[Field]())).map { id => id.toString}
    }
    /*get.flatMap(dictionary =>
      dictionary.
    )*/

  }

  /**
    * Delete single entry identified by its id (name).
    *
    * @param id Name of the field to be deleted.
    * @return Either object with Right indicating the success or failure.
    */
  override def delete(id: String): Future[String] = {
    get.flatMap { dictionary =>
      val field = Field(id)
      val newFields = dictionary.fields.filterNot(_.equals(field))
      if (dictionary.fields.size == newFields.size) {
        Future("id")
      } else {
        dictionaryRepo.update(dictionary.copy(fields = newFields))
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
  override def save(entity: Field): Future[String] = {
    val modifier = Json.obj {
      "$push" -> Json.obj {
        "fields" -> Json.toJson(entity)
      }
    }
    dictionaryRepo.updateCustom(dictionaryId, modifier) map {
      id => id
    }
  }


  /**
    * Counts all items in repo matching criteria.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @return Number of maching elements.
    */
  override def count(criteria: Option[JsObject]): Future[Int] = {
    val futureResult: Future[Traversable[Field]] = find(criteria, None, None, None, None)
    futureResult.map(t => t.size)
  }

  /**
    * Retrieve field(s) from the repo.
    *
    * @param name Name of object.
    * @return Fields in the dicitonary with exact name match.
    */
  override def get(name: String): Future[Option[Field]] =
  {
    get.map(dictionary => dictionary.fields.find(_.name.equals(name)))
  }

  /**
    * TODO: use pagination and projection parameters or remove them.
    * Use mongo modifier slice for projection
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
     criteria: Option[JsObject] = None,
     orderBy: Option[JsObject] = None,
     projection: Option[JsObject] = None,
     limit: Option[Int] = None,
     page: Option[Int] = None
  ): Future[Traversable[Field]] = {

    /*
    // helper for pagination
    val pageIdx = if(page.isEmpty) 0 else page.get;
    val pageOffset = if(limit.isEmpty) count().map(x => x) else limit.get;

    // extract criteria
    val useCriteria = criteria match {
      case None => Json.obj()
      case Some(c) => c
    }*/

    /*dictionaryRepo.find(criteria, orderBy, projection, limit, page).map(x =>
      x.map(dict =>
        dict.fields
      )
    )
    */

    /*val modifier = Json.obj {
      "find" -> Json.obj {
        "comments" -> Json.obj(
          "$slice" -> Json.toJson(Seq(pageIdx, pageOffset))
        )
      }
    }*/

    get.map(dictionary => dictionary.fields)
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
