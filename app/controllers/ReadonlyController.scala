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
protected abstract class ReadonlyController[E : Format, ID](repo: AsyncReadonlyRepo[E, ID]) extends Controller {

  @Inject var messagesApi: MessagesApi = _

  private val DEFAULT_LIMIT = Seq("20")

  protected def listViewColumns : Option[List[String]] = None

  protected def showView(
    id : ID,
    item : E
  )(implicit msg: Messages, request: RequestHeader) : Html

  protected def listView(
    currentPage: Page[E],
    currentOrderBy: String,
    currentFilter: String
  )(implicit msg: Messages, request: RequestHeader) : Html

  protected def toJsonCriteria(string : String) : Option[JsObject]


  def get(id: ID) = Action.async { implicit request =>
    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ){ entity =>
      implicit val msg = messagesApi.preferred(request)

      Ok(showView(id, entity))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the edit process")
        InternalServerError(t.getMessage)
    }
  }

  def getRest(id: ID) = Action.async {
    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    )(entity =>
      Ok(Json.toJson(entity)))
    )
  }

  def findRest(page: Int, orderBy: String, query: String) = Action.async { implicit request =>
    val limit = 20
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
    val limit = 20
    val criteria = toJsonCriteria(query)
    val sort = toJsonSort(orderBy)

    val futureItems = repo.find(criteria, sort, listViewProjection, Some(limit), Some(page))
    val futureCount = repo.count(criteria)
    futureItems.zip(futureCount).map({ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, page, page * limit, count), orderBy, query))
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
    val limit = 20
    val futureItems = repo.find(None, None, listViewProjection, Some(limit), None)
    val futureCount = repo.count(None)
    futureItems.zip(futureCount).map({ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, 0, 0, count), "", ""))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }

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