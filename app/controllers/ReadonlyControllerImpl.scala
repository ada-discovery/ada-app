package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.util.{ConditionType, FilterCondition}
import be.objectify.deadbolt.scala.DeadboltActions
import dataaccess._
import models._
import persistence._
import play.api.Logger
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

trait ReadonlyController[ID] {

  def get(id: ID): Action[AnyContent]

  def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent]

  def listAll(orderBy: String): Action[AnyContent]
}

/**
 * Generic async readonly controller
 *
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
protected abstract class ReadonlyControllerImpl[E: Format, ID](protected val repo: AsyncReadonlyRepo[E, ID]) extends Controller with ReadonlyController[ID] {

  @Inject var messagesApi: MessagesApi = _
  @Inject var deadbolt: DeadboltActions = _

  protected val pageLimit = 20

  protected val timeout = 200000 millis

  protected def repoHook = repo

  protected def listViewColumns : Option[Seq[String]] = None

  protected def showView(
    id : ID,
    item : E
  )(implicit msg: Messages, request: Request[_]) : Html

  protected def listView(
    currentPage: Page[E]
  )(implicit msg: Messages, request: Request[_]) : Html

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
  def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = Action.async { implicit request =>
    val (futureItems, futureCount) = getFutureItemsAndCount(page, orderBy, filter)
    futureItems.zip(futureCount).map{ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      render {
        case Accepts.Html() => Ok(listView(Page(items, page, page * pageLimit, count, orderBy, filter)))
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
  def listAll(orderBy: String) = Action.async { implicit request =>
    val (futureItems, futureCount) = getFutureItemsAndCount(None, orderBy, Nil, Nil, None)
    futureItems.zip(futureCount).map{ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      render {
        case Accepts.Html() => Ok(listView(Page(items, 0, 0, count, orderBy, Nil)))
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
    filter: Seq[FilterCondition]
  ): (Future[Traversable[E]], Future[Int]) =
    getFutureItemsAndCount(Some(page), orderBy, filter, listViewColumns.getOrElse(Nil), Some(pageLimit))

  protected def getFutureItemsAndCount(
    page: Option[Int],
    orderBy: String,
    filter: Seq[FilterCondition],
    projection: Seq[String],
    limit: Option[Int]
  ): (Future[Traversable[E]], Future[Int]) = {
    val sort = toSort(orderBy)
    val criteria = toCriteria(filter)

    val futureItems = repo.find(criteria, sort, projection, limit, page)
    val futureCount = repo.count(criteria)
    (futureItems, futureCount)
  }

  protected def getFutureItems(
    page: Option[Int],
    orderBy: String,
    filter: Seq[FilterCondition],
    projection: Seq[String],
    limit: Option[Int]
  ): Future[Traversable[E]] = {
    val sort = toSort(orderBy)
    val criteria = toCriteria(filter)

    repo.find(criteria, sort, projection, limit, page)
  }

  protected def getFutureCount(filter: Seq[FilterCondition]): Future[Int] = {
    val criteria = toCriteria(filter)

    repo.count(criteria)
  }

  import _root_.util.ConditionType._

  protected def toCriteria(filter: Seq[FilterCondition]): Seq[Criterion[Any]] = {
    filter.map{ filterCondition =>
      val fieldName = filterCondition.fieldName
      val value =  filterCondition.value
      filterCondition.conditionType match {
        case Equals => EqualsCriterion(fieldName, value)
        case RegexEquals => RegexEqualsCriterion(fieldName, value)
        case NotEquals => NotEqualsCriterion(fieldName, value)
        case In => InCriterion(fieldName, value.split(",").map(_.trim))
        case NotIn => NotInCriterion(fieldName, value.split(",").map(_.trim))
        case Greater => GreaterCriterion(fieldName, value.toDouble)
        case Less => LessCriterion(fieldName, value.toDouble)
      }
    }
  }

  /**
    * TODO: Move this into utility object.
    * Convert String into a Sort class.
    *
    * @param fieldName Reference column sorting.
    * @return Option with Sort class indicating an order (asc/desc). None, if string is empty.
    */
  protected def toSort(fieldName : String): Seq[Sort] =
    if (fieldName.nonEmpty) {
      val sort = if (fieldName.startsWith("-"))
        DescSort(fieldName.substring(1, fieldName.length))
      else
        AscSort(fieldName)
      Seq(sort)
    } else
      Nil

  protected def getParamMap(implicit request: Request[AnyContent]): Map[String, Seq[String]] = {
    val body = request.body
    if (body.asFormUrlEncoded.isDefined)
      body.asFormUrlEncoded.get
    else if (body.asMultipartFormData.isDefined)
      body.asMultipartFormData.get.asFormUrlEncoded
    else
      throw new AdaException("FormUrlEncoded or MultipartFormData request expected.")
  }

  protected def getParamValue(paramKey: String)(implicit request: Request[AnyContent]) =
    getParamMap.get(paramKey).get.head

  protected def result[T](future: Future[T]): T =
    Await.result(future, timeout)
}