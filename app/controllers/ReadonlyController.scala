package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import models.{Identity, Page}
import persistence.{AsyncReadonlyRepo, AsyncCrudRepo}
import play.api.Logger
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.Future
import scala.util._

/**
 * Generic async readonly controller
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
protected abstract class ReadonlyController[E : Format, ID](protected val repo: AsyncReadonlyRepo[E, ID]) extends Controller {

  @Inject var messagesApi: MessagesApi = _

  private val DEFAULT_LIMIT = 20

  protected def repoHook = repo

  protected def listViewColumns : Option[List[String]] = None

  protected def showView(
    id : ID,
    item : E
  )(implicit msg: Messages, request: RequestHeader) : Html

  protected def listView(
    currentPage: Page[E]
  )(implicit msg: Messages, request: RequestHeader) : Html

  protected def toJsonCriteria(string : String) : Option[JsObject]


  /**
   * Retrieve single object by its BSON Id.
   * NotFound response is generated if key does not exists.
   *
   * @param id BSON id/ primary key of the object.
   */
  def get(id: ID) = Action.async { implicit request =>
    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ){ entity =>
      implicit val msg = messagesApi.preferred(request)

      Ok(showView(id, entity))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the get process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Return response with json representation of object with given key.
    * Used for rest interface.
    *
    * @param id primary key of object
    */
  def getRest(id: ID) = Action.async {
    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    )(entity =>
      Ok(Json.toJson(entity)))
    )
  }

  /**
    * Return reponse with json representation of all objects mathing the query string.
    * Objects are ordered by a reference column and split into chunks.
    * Used for rest interface.
    *
    * @see find
    * @param page index for the chunk which is to be returned.
    * @param orderBy label of the reference column for sorting.
    * @param query query string for matchng entries. Use empty string to retrieve all objects.
    * @return
    */
  def findRest(page: Int, orderBy: String, query: String) = Action.async { implicit request =>
    val limit = DEFAULT_LIMIT
    val criteria = toJsonCriteria(query)
    val sort = toJsonSort(orderBy)

    val futureItems = repo.find(criteria, sort, listViewProjection, Some(limit), Some(page))
    val futureCount = repo.count(criteria)
    futureItems.zip(futureCount).map({ case (items, count) =>
      Ok(Json.toJson(items))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }

  /**
   * Display the paginated list.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param query Filter applied on items
   */
  def find(page: Int, orderBy: String, query: String) = Action.async { implicit request =>
    val limit = DEFAULT_LIMIT
    val criteria = toJsonCriteria(query)
    val sort = toJsonSort(orderBy)

    val futureItems = repo.find(criteria, sort, listViewProjection, Some(limit), Some(page))
    val futureCount = repo.count(criteria)
    futureItems.zip(futureCount).map({ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, page, page * limit, count, orderBy, query)))
    }).recover {
       case t: TimeoutException =>
         Logger.error("Problem found in the list process")
         InternalServerError(t.getMessage)
    }
  }

  /**
   * Display all items in a paginated fashion.
   *
   * @param orderBy Column to be sorted
   */
  def listAll(orderBy: Int) = Action.async { implicit request =>
    val limit = DEFAULT_LIMIT
    val futureItems = repo.find(None, None, listViewProjection, Some(limit), None)
    val futureCount = repo.count(None)
    futureItems.zip(futureCount).map({ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, 0, 0, count, "", "")))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Return response with json represenation of all stored objects
    * Used for rest interface.
    *
    */
  def listAllRest = Action.async { implicit request =>
    val futureItems = repo.find(None, None, listViewProjection, None, None)
    futureItems.map { items =>
      Ok(Json.toJson(items))
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }
  
  protected def toJsonSort(string : String) =
    if (!string.isEmpty) {
      if (string.startsWith("-"))
        Some(Json.obj(string.substring(1) -> -1))
      else
        Some(Json.obj(string -> 1))
    } else
      None

  protected def listViewProjection =
    listViewColumns.map(columns =>
      JsObject(columns.map(column => (column, Json.toJson(1)))))
}