package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.util.{FilterCondition, FilterSpec}
import models.{Identity, Page}
import persistence._
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

  protected def listViewColumns : Option[Seq[String]] = None

  protected def showView(
    id : ID,
    item : E
  )(implicit msg: Messages, request: RequestHeader) : Html

  protected def listView(
    currentPage: Page[E]
  )(implicit msg: Messages, request: RequestHeader) : Html

  /**
   * Retrieve single object by its Id.
   * NotFound response is generated if key does not exists.
   *
   * @param id id/ primary key of the object.
   */
  def get(id: ID) = Action.async { implicit request =>
    getCall(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ){ entity =>
      implicit val msg = messagesApi.preferred(request)

      render {
        case Accepts.Html() => Ok(showView(id, entity))
        case Accepts.Json() => Ok(Json.toJson(entity))
      }
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the get process")
        InternalServerError(t.getMessage)
    }
  }

  protected def getCall(id: ID): Future[Option[E]] = repo.get(id)

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

      render {
        case Accepts.Html() => Ok(listView(Page(items, page, page * limit, count, orderBy, filter)))
        case Accepts.Json() => Ok(Json.toJson(items))
      }
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

      render {
        case Accepts.Html() => Ok(listView(Page(items, 0, 0, count, "", new FilterSpec())))
        case Accepts.Json() => Ok(Json.toJson(items))
      }
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
    val sort = toSort(orderBy)
    val projection = toJsonProjection(projectedFieldNames)

    val futureItems = repox.find(criteria, sort, projection, Some(limit), Some(page))
    val futureCount = repox.count(criteria)
    (futureItems, futureCount)
  }

  /**
    * TODO: Move this into utility object.
    * Convert String into a Sort class.
    *
    * @param fieldName Reference column sorting.
    * @return Option with Sort class indicating an order (asc/desc). None, if string is empty.
    */
  protected def toSort(fieldName : String): Option[Seq[Sort]] =
    if (fieldName.nonEmpty) {
      val sort = if (fieldName.startsWith("-"))
        DescSort(fieldName)
      else
        AscSort(fieldName)
      Some(Seq(sort))
    } else
      None

  protected def toJsonProjection(fieldNames : Option[Seq[String]]): Option[JsObject] =
    fieldNames.map(fieldNames =>
      JsObject(fieldNames.map(fieldName => (fieldName, Json.toJson(1)))))
}