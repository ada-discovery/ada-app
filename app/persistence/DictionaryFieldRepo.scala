package persistence

import javax.inject.{Inject, Named}

import models.{Dictionary, Field, FieldType}
import persistence.RepoTypeRegistry.{DictionaryRootRepo, JsObjectCrudRepo}
import play.api.libs.json.{JsArray, JsString, JsObject, Json}
import reactivemongo.bson.BSONObjectID
import scala.concurrent.{Await, Future}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import models.Dictionary._
import scala.concurrent.duration._
import play.modules.reactivemongo.json.commands.JSONAggregationFramework.{Push, Ascending, Descending}

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

  /**
    * Forwards call to getByDataSetName.
    *
    * @see getByDataSetName()
    * @return Currently selected data set.
    */
  override def get: Future[Dictionary] =
    getByDataSetName.map(dictionaries =>
      if (dictionaries.isEmpty)
        throw new IllegalAccessException("Dictionary was not initialized")
      else
        dictionaries.head
    )

  /**
    * Initialize dictionary if it does not exist.
    *
    * @return true, if initialization required.
    */
  override def initIfNeeded: Future[Boolean] = synchronized {
    val responseFuture = getByDataSetName.flatMap(dictionaries =>
      if (dictionaries.isEmpty)
        dictionaryRepo.save(Dictionary(None, dataSetName, List[Field]())).map(_ => true)
      else
        Future(false)
    )

    // init dictionary id: TODO: move after dictionaryrepo injection
    responseFuture.map { response =>
      dictionaryId;
      response
    }
  }

  /**
    * Internally used to search the dictionary with current data set name.
    *
    * @return Traversable with all dictionaries matching the current data set name.
    */
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
    * Delets all fields in the dictionary.
    * @see update()
    *
    * @return Nothing (Unit)
    */
  override def deleteAll: Future[Unit] = {
    val modifier = Json.obj {
      "$set" -> Json.obj {
        "fields" -> List[Field]()
      }
    }
    dictionaryRepo.updateCustom(dictionaryId, modifier)
  }

  /**
    * Delete single entry identified by its id (name).
    *
    * @param name Name of the field to be deleted.
    * @return Nothing (Unit)
    */
  override def delete(name: String): Future[Unit] = {
    val modifier = Json.obj {
      "$pull" -> Json.obj {
        "fields" -> Json.obj {
          "name" -> name
        }
      }
    }
    dictionaryRepo.updateCustom(dictionaryId, modifier)
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
    dictionaryRepo.updateCustom(dictionaryId, modifier) map { _ =>
      entity.name
    }
  }

  /**
    * Counts all items in repo matching criteria.
    *
    * @param criteria Filtering criteria object. Use a JsObject to filter according to value of reference column. Use None for no filtering.
    * @return Number of matching elements.
    */
  override def count(criteria: Option[JsObject]): Future[Int] = {
//    val criteria =
//      if (subCriteria.isDefined) {
//        val extSubCriteria = subCriteria.get.fields.map { case (name, value) => ("fields." + name, value) }
//        JsObject(extSubCriteria) + ("dataSetName" -> Json.toJson(dataSetName))
//      } else
//        Json.obj("dataSetName" -> dataSetName)

    // TODO: optimize me
    val futureFields = find(criteria)
    futureFields.map(_.size)
  }

  /**
    * Retrieve field(s) from the repo.
    *
    * @param name Name of object.
    * @return Fields in the dictionary with exact name match.
    */
  override def get(name: String): Future[Option[Field]] = {
    val futureFields = find(Some(Json.obj("name" -> name)))
    futureFields.map(fields =>
      if (fields.isEmpty)
        None
      else
        Some(fields.head)
    )
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
    orderBy: Option[Seq[Sort]] = None,
    projection: Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Future[Traversable[Field]] = {

//    val criteria =
//      if (subCriteria.isDefined)
//        Json.obj("fields" -> Json.obj(
//          "$elemMatch" -> subCriteria.get
//        )) + ("dataSetName" -> Json.toJson(dataSetName))
//      else
//        Json.obj("dataSetName" -> dataSetName)
//
//    val projection =
//      if (limit.isDefined)
//        Some(
//          Json.obj("fields" -> Json.obj(
//            "$slice" -> Json.toJson(Seq(page.getOrElse(0) * limit.get, limit.get))
//          )))
//      else
//        None

    val fullCriteria =
      if (criteria.isDefined) {
        val extSubCriteria = criteria.get.fields.map { case (name, value) => ("fields." + name, value) }
        JsObject(extSubCriteria) + ("dataSetName" -> Json.toJson(dataSetName))
      } else
        Json.obj("dataSetName" -> dataSetName)

    val fullProjection = projection.map{ proj =>
      val extFields = proj.fields.map { case (name, value) => ("fields." + name, value) }
      JsObject(extFields)
    }

    val result = dictionaryRepo.findAggregate(
      criteria = Some(fullCriteria),
      orderBy = orderBy,
      projection = None, //fullProjection,
      idGroup = Some(JsString("$dataSetName")),
      groups = Some(Seq("fields" -> Push("fields"))),
      unwindFieldName = Some("fields"),
      limit = limit,
      page = page
    )

    result.map { result =>
      if (result.nonEmpty) {
        val jsonFields = (result.head \ "fields").as[JsArray].value
        println(jsonFields)
        jsonFields.map(_.as[Field])
      } else
        Seq[Field]()
    }
  }
}