package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.util.{FilterCondition, FilterSpec}
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

  protected val limit = 20

  protected def repoHook = repo

  protected def listViewColumns : Option[List[String]] = None

  protected def showView(
    id : ID,
    item : E
  )(implicit msg: Messages, request: RequestHeader) : Html

  protected def listView(
    currentPage: Page[E]
  )(implicit msg: Messages, request: RequestHeader) : Html

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
    * Display the paginated list.
    *
    * @param page Current page number (starts from 0)
    * @param orderBy Column to be sorted
    * @param filter Filter applied on items
    */
  def find(page: Int, orderBy: String, filter: FilterSpec) = Action.async { implicit request =>
    val (futureItems, futureCount) = getFutureItemsAndCount(page ,orderBy, filter)
    futureItems.zip(futureCount).map{ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, page, page * limit, count, orderBy, filter)))
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Return reponse with json representation of all objects mathing the query string.
    * Objects are ordered by a reference column and split into chunks.
    * Used for rest interface.
    *
    * @see find
    * @param page index for the chunk which is to be returned.
    * @param orderBy label of the reference column for sorting.
    * @param filter query string for matchng entries. Use empty string to retrieve all objects.
    * @return
    */
  def findRest(page: Int, orderBy: String, filter: FilterSpec) = Action.async { implicit request =>
    val (futureItems, futureCount) = getFutureItemsAndCount(page ,orderBy, filter)
    futureItems.zip(futureCount).map{ case (items, count) =>
      Ok(Json.toJson(items))
    }.recover {
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
    val (futureItems, futureCount) = getFutureItemsAndCount(0 ,"", new FilterSpec())
    futureItems.zip(futureCount).map{ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, 0, 0, count, "", new FilterSpec())))
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    *
    * Return response with json represenation of all stored objects
    * Used for rest interface.
    *
    */
  def listAllRest = Action.async { implicit request =>
    val (futureItems, futureCount) = getFutureItemsAndCount(0 ,"", new FilterSpec())
    futureItems.map { items =>
      Ok(Json.toJson(items))
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }

  protected def getFutureItemsAndCount(
    page: Int,
    orderBy: String,
    filter : FilterSpec
  ): (Future[Traversable[E]], Future[Int]) =
    getFutureItemsAndCount(repo)(page, orderBy, filter, listViewColumns)

  protected def getFutureItemsAndCount[T](
    repox : AsyncReadonlyRepo[T, _]
  )(
    page: Int,
    orderBy: String,
    filter : FilterSpec,
    projectedFieldNames : Option[Seq[String]]
  ): (Future[Traversable[T]], Future[Int]) = {
    val criteria = filter.toJsonCriteria
    val sort = toJsonSort(orderBy)
    val projection = toJsonProjection(projectedFieldNames)

    val futureItems = repox.find(criteria, sort, projection, Some(limit), Some(page))
    val futureCount = repox.count(criteria)
    (futureItems, futureCount)
  }

  /**
    * TODO: Move this into utility object.
    * Convert String into Json object for sorting.
    *
    * @param string Reference column sorting.
    * @return Option with JsObject indicating sorting index. None, if string is empty.
    */
  protected def toJsonSort(string : String): Option[JsObject] =
    if (!string.isEmpty) {
      if (string.startsWith("-"))
        Some(Json.obj(string.substring(1) -> -1))
      else
        Some(Json.obj(string -> 1))
    } else
      None

  protected def toJsonProjection(fieldNames : Option[Seq[String]]): Option[JsObject] =
    fieldNames.map(fieldNames =>
      JsObject(fieldNames.map(fieldName => (fieldName, Json.toJson(1)))))
}