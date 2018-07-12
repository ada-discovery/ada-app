package controllers.core

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import controllers.WebJarAssets
import dataaccess._
import models._
import play.api.{Configuration, Logger}
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import models.FilterCondition.toCriterion

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
protected[controllers] abstract class ReadonlyControllerImpl[E: Format, ID] extends Controller
  with ReadonlyController[ID]
  with HasListView[E]
  with HasShowView[E, ID] {

  @Inject var messagesApi: MessagesApi = _
  @Inject var deadbolt: DeadboltActions = _
  @Inject var webJarAssets: WebJarAssets = _
  @Inject var configuration: Configuration = _

  protected val pageLimit = 20

  protected val timeout = 200 seconds

  protected def repo: AsyncReadonlyRepo[E, ID]

  protected def repoHook = repo

  protected def listViewColumns: Option[Seq[String]] = None

  protected def entityNameKey = ""
  protected lazy val entityName = if (entityNameKey.isEmpty) "Item" else messagesApi.apply(entityNameKey + ".displayName")

  protected def formatId(id: ID) = id.toString

//  protected implicit def webContext(implicit request: AuthenticatedRequest[_]) = WebContext(messagesApi)
  protected implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets, configuration)
  }

  //  protected implicit def authenticatedRequest[A](implicit request: Request[A]) = new AuthenticatedRequest(request, None)

  /**
   * Retrieve single object by its Id.
   * NotFound response is generated if key does not exists.
   *
   * @param id id/ primary key of the object.
   */
  def get(id: ID) = Action.async { implicit request =>
    {
      for {
        // retrieve the item
        item <- repo.get(id)

        // create a view data if the item has been found
        viewData <- item.fold(
          Future(Option.empty[ShowViewData])
        ) { entity =>
          getShowViewData(id, entity)(request).map(Some(_))
        }
      } yield
        item match {
          case None => NotFound(s"$entityName #${formatId(id)} not found")
          case Some(entity) =>
            render {
              case Accepts.Html() => Ok(showViewWithContext(viewData.get))
              case Accepts.Json() => Ok(Json.toJson(entity))
            }
        }
    }.recover {
        case t: TimeoutException =>
          Logger.error("Problem found in the get process")
          InternalServerError(t.getMessage)
    }
  }

  /**
    * Display the paginated list.
    *
    * @param page Current page number (starts from 0)
    * @param orderBy Column to be sorted
    * @param filter Filter applied on items
    */
  def find(
    page: Int,
    orderBy: String,
    filter: Seq[FilterCondition]
  ) = Action.async { implicit request =>
    {
      for {
        (items, count) <- getFutureItemsAndCount(page, orderBy, filter)

        viewData <- getListViewData(
          Page(items, page, page * pageLimit, count, orderBy, Some(new models.Filter(filter)))
        )(request)
      } yield
        render {
          case Accepts.Html() => Ok(listViewWithContext(viewData))
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
    {
      for {
        (items, count) <- getFutureItemsAndCount(None, orderBy, Nil, Nil, None)

        viewData <- getListViewData(
          Page(items, 0, 0, count, orderBy, None)
        )(request)
      } yield
        render {
          case Accepts.Html() => Ok(listViewWithContext(viewData))
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
  ): Future[(Traversable[E], Int)] =
    getFutureItemsAndCount(Some(page), orderBy, filter, listViewColumns.getOrElse(Nil), Some(pageLimit))

  protected def getFutureItemsAndCount(
    page: Option[Int],
    orderBy: String,
    filter: Seq[FilterCondition],
    projection: Seq[String],
    limit: Option[Int]
  ): Future[(Traversable[E], Int)] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    for {
      criteria <- toCriteria(filter)
      itemsCount <- {
        val itemsFuture = repo.find(criteria, sort, projection, limit, skip)
        val countFuture = repo.count(criteria)

        for { items <- itemsFuture; count <- countFuture} yield
          (items, count)
      }
    } yield
      itemsCount
  }

  protected def getFutureItems(
    page: Option[Int],
    orderBy: String,
    filter: Seq[FilterCondition],
    projection: Seq[String],
    limit: Option[Int]
  ): Future[Traversable[E]] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    toCriteria(filter).flatMap ( criteria =>
      repo.find(criteria, sort, projection, limit, skip)
    )
  }

  protected def getFutureItemsForCriteria(
    page: Option[Int],
    orderBy: String,
    criteria: Seq[Criterion[Any]],
    projection: Seq[String],
    limit: Option[Int]
  ): Future[Traversable[E]] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    repo.find(criteria, sort, projection, limit, skip)
  }

  protected def getFutureCount(filter: Seq[FilterCondition]): Future[Int] =
    toCriteria(filter).flatMap(repo.count)

  protected def toCriteria(
    filter: Seq[FilterCondition]
  ): Future[Seq[Criterion[Any]]] = {
    val fieldNames = filter.seq.map(_.fieldName)

    filterValueConverters(fieldNames).map( valueConverters =>
      filter.map(toCriterion(valueConverters)).flatten
    )
  }

  protected def filterValueConverters(
    fieldNames: Traversable[String]
  ): Future[Map[String, String => Option[Any]]] =
    Future(Map())

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

  protected def result[T](future: Future[T]): T =
    Await.result(future, timeout)
}