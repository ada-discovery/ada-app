package persistence

import models.Identity
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.modules.reactivemongo.json.commands.JSONAggregationFramework.{Push, SumValue}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait SubordinateObjectRepo[E, ID] extends AsyncCrudRepo[E, ID] {
  // TODO: Do we need an init method?
  def initIfNeeded: Future[Boolean]
}

protected[persistence] abstract class SubordinateObjectMongoAsyncCrudRepo[E: Format, ID: Format, ROOT_E: Format,  ROOT_ID: Format](
    listName: String,
    rootRepo: MongoAsyncCrudExtraRepo[ROOT_E, ROOT_ID])(
    implicit identity: Identity[E, ID], rootIdentity: Identity[ROOT_E, ROOT_ID]
  ) extends SubordinateObjectRepo[E, ID] {

  protected def getDefaultRoot: ROOT_E

  protected def getRootObject: Future[Option[ROOT_E]]

  /**
    * Initialize if root object does not exist.
    *
    * @return true, if initialization was required.
    */
  override def initIfNeeded: Future[Boolean] = synchronized {
    val responseFuture = getRootObject.flatMap(rootObject =>
      if (rootObject.isEmpty)
        rootRepo.save(getDefaultRoot).map(_ => true)
      else
        Future(false)
    )

    // init root id
    responseFuture.map { response =>
      rootId;
      response
    }
  }


  private lazy val rootId: ROOT_ID = synchronized {
    val futureId = getRootObject.map(rootObject => rootIdentity.of(rootObject.get).get)
    Await.result(futureId, 120000 millis)
  }

  protected lazy val rootIdSelector = Json.obj(rootIdentity.name -> rootId)

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

    rootRepo.updateCustom(rootIdSelector, modifier) map { _ =>
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
    rootRepo.updateCustom(rootIdSelector, modifier)
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
      rootIdSelector + ((listName + "." + identity.name) -> Json.toJson(id))

    val modifier = Json.obj {
      "$set" -> Json.obj {
        listName + ".$" -> Json.toJson(entity)
      }
    }
    rootRepo.updateCustom(selector, modifier) map { _ =>
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
    rootRepo.updateCustom(rootIdSelector, modifier)
  }

  /**
    * Counts all items in repo matching criteria.
    *
    * @param criteria Filtering criteria object. Use a JsObject to filter according to value of reference column. Use None for no filtering.
    * @return Number of matching elements.
    */
  override def count(criteria: Option[JsObject]): Future[Int] = {
    val rootCriteria = Some(Json.obj(rootIdentity.name -> Json.toJson(rootId)))
    val subCriteria = criteria.map(criteria =>  JsObject(criteria.fields.map { case (name, value) => (listName + "." + name, value) } ))

    val result = rootRepo.findAggregate(
      rootCriteria = rootCriteria,
      subCriteria = subCriteria,
      orderBy = None,
      projection = None,
      idGroup = Some(JsNull),
      groups = Some(Seq("count" -> SumValue(1))),
      unwindFieldName = Some(listName),
      limit = None,
      page = None
    )

    result.map { result =>
      if (result.nonEmpty) {
        (result.head \ "count").as[Int]
      } else
        0
    }
  }

  /**
    * Retrieve subordinate(s) from the repo.
    *
    * @param id Name of object.
    * @return subordinateListNames in the dictionary with exact name match.
    */
  override def get(id: ID): Future[Option[E]] = {
    val futureSubordinates = find(Some(Json.obj(identity.name -> id)))
    futureSubordinates.map(_.headOption)
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
    val rootCriteria = Some(Json.obj(rootIdentity.name -> Json.toJson(rootId)))
    val subCriteria = criteria.map(criteria =>  JsObject(criteria.fields.map { case (name, value) => (listName + "." + name, value) } ))

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

    // TODO: projection can not be passed here since subordinateListName JSON formatter expects ALL attributes to be returned.
    // It could be solved either by making all subordinateListName attributes optional (Option[..]) or introducing a special JSON formatter with default values for each attribute
    val result = rootRepo.findAggregate(
      rootCriteria = rootCriteria,
      subCriteria = subCriteria,
      orderBy = fullOrderBy,
      projection = None, //fullProjection,
      idGroup = Some(JsNull),
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