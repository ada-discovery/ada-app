package persistence.dataset

import models.DataSetFormattersAndIds._
import models.{Identity, Dictionary, Field, Category}
import persistence.{DescSort, AscSort, Sort, AsyncCrudRepo}
import persistence.RepoTypes.{CategoryRepo, DictionaryRootRepo}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.modules.reactivemongo.json.BSONObjectIDFormat
import play.modules.reactivemongo.json.commands.JSONAggregationFramework.Push
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait DictionarySubordinateRepo[E, ID] extends AsyncCrudRepo[E, ID] {
  def initIfNeeded: Future[Boolean]
}

protected[persistence] class DictionarySubordinateMongoAsyncCrudRepo[E: Format, ID: Format](
    listName: String,
    dataSetId: String,
    dictionaryRepo: DictionaryRootRepo)(
    implicit identity: Identity[E, ID]
  ) extends DictionarySubordinateRepo[E, ID] {

  /**
    * Initialize dictionary if it does not exist.
    *
    * @return true, if initialization was required.
    */
  override def initIfNeeded: Future[Boolean] = synchronized {
    val responseFuture = getByDataSetId.flatMap(dictionaries =>
      if (dictionaries.isEmpty)
        dictionaryRepo.save(Dictionary(None, dataSetId, Seq[Field](), Seq[Category]())).map(_ => true)
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

  protected lazy val dictionaryIdSelector = Json.obj(DictionaryIdentity.name -> dictionaryId)

  /**
   * Converts the given subordinateListName into Json format and calls updateCustom() to update/ save it in the repo.
    *
    * @see updateCustom()
    * @param entity to be updated/ saved
   * @return subordinateListName name as a confirmation of success.
   */
  override def save(entity: E): Future[ID] = {
    val modifier = Json.obj {
      "$push" -> Json.obj {
        listName -> Json.toJson(entity)
      }
    }

    dictionaryRepo.updateCustom(dictionaryIdSelector, modifier) map { _ =>
      identity.of(entity).get
    }
  }

  /**
   * Delete single entry identified by its id (name).
   *
   * @param id Id of the subordinate to be deleted.
   * @return Nothing (Unit)
   */
  override def delete(id: ID): Future[Unit] = {
    val modifier = Json.obj {
      "$pull" -> Json.obj {
        listName -> Json.obj {
          identity.name -> id
        }
      }
    }
    dictionaryRepo.updateCustom(dictionaryIdSelector, modifier)
  }

  /**
    * Update a single subordinate in repo.
    * The properties of the passed subordinate replace the properties of the subordinate in the repo.
    *
    * @param entity Subordinate to be updated. entity.name must match an existing ID.
    * @return Id as a confirmation of success.
    */
  override def update(entity: E): Future[ID] = {
    val id = identity.of(entity)
    val selector =
      dictionaryIdSelector + ((listName + "." + identity.name) -> Json.toJson(id))

    val modifier = Json.obj {
      "$set" -> Json.obj {
        listName + ".$" -> Json.toJson(entity)
      }
    }
    dictionaryRepo.updateCustom(selector, modifier) map { _ =>
      id.get
    }
  }

  /**
    * Deletes all subordinates in the dictionary.
    *
    * @see update()
    * @return Nothing (Unit)
    */
  override def deleteAll: Future[Unit] = {
    val modifier = Json.obj {
      "$set" -> Json.obj {
        listName -> List[E]()
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
  override def count(criteria: Option[JsObject]): Future[Int] =
    // TODO: optimize me using aggregate count projection
    find(criteria).map(_.size)

  /**
    * Retrieve subordinate(s) from the repo.
    *
    * @param id Name of object.
    * @return subordinateListNames in the dictionary with exact name match.
    */
  override def get(id: ID): Future[Option[E]] = {
    val futureSubordinates = find(Some(Json.obj(identity.name -> id)))
    futureSubordinates.map(subordinates =>
      if (subordinates.isEmpty)
        None
      else
        Some(subordinates.head)
    )
  }

  /**
    * Find object matching the filtering criteria. subordinateListNames may be ordered and only a subset of them used.
    * Pagination options for page limit and page number are available to limit number of returned results.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @param orderBy Column used as reference for sorting. Leave at None to use default.
    * @param projection Defines which columns are supposed to be returned. Leave at None to use default.
    * @param limit Page limit. Use to define chunk sizes for pagination. Leave at None to use default.
    * @param page Page to be returned. Specifies which chunk is returned.
    * @return Traversable subordinateListNames for iteration.
    */
  override def find(
    criteria: Option[JsObject] = None,
    orderBy: Option[Seq[Sort]] = None,
    projection: Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Future[Traversable[E]] = {

    val fullCriteria =
      if (criteria.isDefined) {
        val extSubCriteria = criteria.get.fields.map { case (name, value) => (listName + "." + name, value) }
        JsObject(extSubCriteria) + ("dataSetId" -> Json.toJson(dataSetId))
      } else
        Json.obj("dataSetId" -> dataSetId)

    val fullOrderBy =
      orderBy.map(_.map(
        _ match {
          case AscSort(fieldName) => AscSort(listName + "." + fieldName)
          case DescSort(fieldName) => DescSort(listName + "." + fieldName)
        }
      ))

    val fullProjection =
      projection.map{ proj =>
        val extsubordinateListNames = proj.fields.map { case (name, value) => (listName + "." + name, value) }
        JsObject(extsubordinateListNames)
      }

    // TODO: projection can not be passed here since subordinateListName JSON formatter expects ALL attributes to be returned. It could be solved either by making all subordinateListName attributes optional (Option[..]) or introducing a special JSON formatter with default values for each attribute
    val result = dictionaryRepo.findAggregate(
      criteria = Some(fullCriteria),
      orderBy = fullOrderBy,
      projection = None, //fullProjection,
      idGroup = Some(JsString("$dataSetName")),
      groups = Some(Seq(listName -> Push(listName))),
      unwindFieldName = Some(listName),
      limit = limit,
      page = page
    )

    result.map { result =>
      if (result.nonEmpty) {
        val jsonFields = (result.head \ listName).as[JsArray].value
        jsonFields.map(_.as[E])
      } else
        Seq[E]()
    }
  }
}