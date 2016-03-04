package persistence.dataset

import javax.inject.Inject

import models.DataSetFormattersAndIds._
import models.{Dictionary, Field}
import persistence.{DescSort, AscSort, Sort, AsyncCrudRepo}
import persistence.RepoTypes.{DictionaryRootRepo, JsObjectCrudRepo}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import play.modules.reactivemongo.json.BSONObjectIDFormat
import play.modules.reactivemongo.json.commands.JSONAggregationFramework.Push
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait DictionaryFieldRepo extends AsyncCrudRepo[Field, String] {

  def get: Future[Dictionary]
  def initIfNeeded: Future[Boolean]
}

class DictionaryFieldMongoAsyncCrudRepo(
    private val dataSetId : String
  ) extends DictionaryFieldRepo {

  @Inject var dictionaryRepo: DictionaryRootRepo = _

  /**
    * Forwards call to getByDataSetId.
    *
    * @see getByDataSetId()
    * @return Currently selected data set.
    */
  override def get: Future[Dictionary] =
    getByDataSetId.map(dictionaries =>
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
    val responseFuture = getByDataSetId.flatMap(dictionaries =>
      if (dictionaries.isEmpty)
        dictionaryRepo.save(Dictionary(None, dataSetId, List[Field]())).map(_ => true)
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
  private def getByDataSetId: Future[Traversable[Dictionary]] =
    dictionaryRepo.find(Some(Json.obj("dataSetId" -> dataSetId)))

  private lazy val dictionaryId: BSONObjectID = synchronized {
    val futureId = dictionaryRepo.find(
      Some(Json.obj("dataSetId" -> dataSetId)), None, None
    ).map(_.head._id.get)
    Await.result(futureId, 120000 millis)
  }

  private lazy val dictionaryIdSelector = Json.obj(DictionaryIdentity.name -> dictionaryId)

  /**
   * Converts the given Field into Json format and calls updateCustom() to update/ save it in the repo.
    *
    * @see updateCustom()
    * @param entity Field to be updated/ saved
   * @return Field name as a confirmation of success.
   */
  override def save(entity: Field): Future[String] = {
    val modifier = Json.obj {
      "$push" -> Json.obj {
        "fields" -> Json.toJson(entity)
      }
    }

    dictionaryRepo.updateCustom(dictionaryIdSelector, modifier) map { _ =>
      entity.name
    }
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
    dictionaryRepo.updateCustom(dictionaryIdSelector, modifier)
  }

  /**
    * Update single Field in repo.
    * The properties of the passed field replace the properties of the field in the repo.
    *
    * @param entity Field to be updated. entity.name must match an existing Field.name.
    * @return Field name as a confirmation of success.
    */
  override def update(entity: Field): Future[String] = {
    val selector =
      dictionaryIdSelector + ("fields.name" -> JsString(entity.name))

    val modifier = Json.obj {
      "$set" -> Json.obj {
        "fields.$" -> Json.toJson(entity)
      }
    }
    dictionaryRepo.updateCustom(selector, modifier) map { _ =>
      entity.name
    }
  }

  /**
    * Deletes all fields in the dictionary.
    *
    * @see update()
    * @return Nothing (Unit)
    */
  override def deleteAll: Future[Unit] = {
    val modifier = Json.obj {
      "$set" -> Json.obj {
        "fields" -> List[Field]()
      }
    }
    dictionaryRepo.updateCustom(dictionaryIdSelector, modifier)
  }

  /**
    * Counts all items in repo matching criteria.
    *
    * @param criteria Filtering criteria object. Use a JsObject to filter according to value of reference column. Use None for no filtering.
    * @return Number of matching elements.
    */
  override def count(criteria: Option[JsObject]): Future[Int] = {
    // TODO: optimize me using aggregate count projection
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
    * Find object matching the filtering criteria. Fields may be ordered and only a subset of them used.
    * Pagination options for page limit and page number are available to limit number of returned results.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @param orderBy Column used as reference for sorting. Leave at None to use default.
    * @param projection Defines which columns are supposed to be returned. Leave at None to use default.
    * @param limit Page limit. Use to define chunk sizes for pagination. Leave at None to use default.
    * @param page Page to be returned. Specifies which chunk is returned.
    * @return Traversable fields for iteration.
    */
  override def find(
    criteria: Option[JsObject] = None,
    orderBy: Option[Seq[Sort]] = None,
    projection: Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Future[Traversable[Field]] = {

    val fullCriteria =
      if (criteria.isDefined) {
        val extSubCriteria = criteria.get.fields.map { case (name, value) => ("fields." + name, value) }
        JsObject(extSubCriteria) + ("dataSetId" -> Json.toJson(dataSetId))
      } else
        Json.obj("dataSetId" -> dataSetId)

    val fullOrderBy =
      orderBy.map(_.map(
        _ match {
          case AscSort(fieldName) => AscSort("fields." + fieldName)
          case DescSort(fieldName) => DescSort("fields." + fieldName)
        }
      ))

    val fullProjection =
      projection.map{ proj =>
        val extFields = proj.fields.map { case (name, value) => ("fields." + name, value) }
        JsObject(extFields)
      }

    // TODO: projection can not be passed here since Field JSON formatter expects ALL attributes to be returned. It could be solved either by making all Field attributes optional (Option[..]) or introducing a special JSON formatter with default values for each attribute
    val result = dictionaryRepo.findAggregate(
      criteria = Some(fullCriteria),
      orderBy = fullOrderBy,
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
        jsonFields.map(_.as[Field])
      } else
        Seq[Field]()
    }
  }
}