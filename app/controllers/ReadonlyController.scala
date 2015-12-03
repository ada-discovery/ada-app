package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import models.{Page, Identity}
import persistence.AsyncCrudRepo
import play.api.Logger
import play.api.routing.Router
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import play.api.data.Form

import scala.concurrent.Future
import scala.util._
import play.twirl.api.Html

/**
 * Generic async CRUD controller
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
abstract class ReadonlyController[E: Format, ID](
    val repo: AsyncCrudRepo[E, ID]
  )(implicit identity: Identity[E, ID]) extends Controller {

  @Inject val messagesApi: MessagesApi

  private val DEFAULT_LIMIT = Seq("20")

  def createView(implicit msg: Messages, request: RequestHeader) : Option[Html] = None
  def getView(item : E, id : ID)(implicit msg: Messages, request: RequestHeader) : Option[Html] = None
  def editView(item : E, id : ID)(implicit msg: Messages, request: RequestHeader) : Option[Html] = None
  def listView(currentPage: Page[E], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) : Option[Html] = None

  def home : Result
  def defaultCreateEntity : E

  def create = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(createView)
  }

  def get(id: ID) = Action.async { implicit request =>
    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ){ entity =>
      implicit val msg = messagesApi.preferred(request)

      Ok(getView(entity, id))
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

  def edit(id: ID) = Action.async { implicit request =>
//    println(request.tags.get(Router.Tags.RouteController))
//    println(request.tags.get(Router.Tags.RouteActionMethod))

    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ) { entity =>
      implicit val msg = messagesApi.preferred(request)
      Ok(editView(entity, id))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the edit process")
        InternalServerError(t.getMessage)
    }
  }

  def findRest(page: Int, orderBy: String, query: String) = Action.async { implicit request =>
    val limit = 5
    val criteria = Json.parse(query).as[JsObject]
    val sort = Json.parse(orderBy).as[JsObject]

    val futureItems = repo.find(Some(criteria), Some(sort), None, Some(limit), Some(page))
    val futureCount = repo.count(Some(criteria))
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
    val limit = 5
    val criteria = Json.parse(query).as[JsObject]
    val sort = Json.parse(orderBy).as[JsObject]

    val futureItems = repo.find(Some(criteria), Some(sort), None, Some(limit), Some(page))
    val futureCount = repo.count(Some(criteria))
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
    val futureItems = repo.find(None, None, None, Some(limit), None)
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
    val futureItems = repo.find(None, None, None, None, None)
    futureItems.map { items =>
      Ok(Json.toJson(items))
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }

  def save = Action.async { implicit request =>
    form.bindFromRequest.fold(
    { formWithErrors =>
      implicit val msg = messagesApi.preferred(request)
      Future.successful(BadRequest(createView(formWithErrors)))
    },
    item => {
      val savedItemId = repo.save(item)
      savedItemId.map {
        case Right(id) => home.flashing("success" -> s"Item ${id} has been created")
        case Left(err) => BadRequest(err)
      }.recover {
        case t: TimeoutException =>
          Logger.error("Problem found in the update process")
          InternalServerError(t.getMessage)
      }
    })
  }

  def saveRest = Action.async(parse.json) { implicit request =>
    form.bindFromRequest.fold(
    { formWithErrors =>
      implicit val msg = messagesApi.preferred(request)
      Future.successful(BadRequest(formWithErrors.errorsAsJson))
    },
    entity =>
      repo.save(entity).map {
        case Right(id) => Created(Json.obj("message" -> "Item successly created", "id" -> id.toString))
        case Left(err) => BadRequest(err)
      }
    )
  }

  def update(id: ID) = Action.async { implicit request =>
    form.bindFromRequest.fold(
    { formWithErrors =>
      implicit val msg = messagesApi.preferred(request)
      Future.successful(BadRequest(editView(id, formWithErrors)))
    },
    item => {
      val savedItemId = repo.update(identity.set(item, id))
      savedItemId.map {
        case Right(id) => home.flashing("success" -> s"Item ${id} has been updated")
        case Left(err) => BadRequest(err)
      }.recover {
        case t: TimeoutException =>
          Logger.error("Problem found in the update process")
          InternalServerError(t.getMessage)
      }
    })
  }

  def updateRest(id: ID) = Action.async(parse.json) { implicit request =>
    parseValidateAndProcess[E] { entity =>
      repo.update(identity.set(entity, id)).map {
        case Right(id) => Ok(Json.obj("message" -> "Item successly updated", "id" -> id.toString))
        case Left(err) => BadRequest(err)
      }
    }
  }

  def delete(id: ID) = Action.async {
    repo.delete(id).map {
      case Right(id) => home.flashing("success" -> s"Item ${id} has been deleted")
      case Left(err) => BadRequest(err)
    }.recover {
      case t: TimeoutException =>
        Logger.error(s"Problem deleting item ${id}")
        InternalServerError(t.getMessage)
    }
  }

  def deleteRest(id: ID) = Action.async {
    repo.delete(id).map {
      case Right(id) => Ok(Json.obj("message" -> "Item successly deleted", "id" -> id.toString))
      case Left(err) => BadRequest(err)
    }
  }

  private def parseValidateAndProcess[T: Reads](t: T => Future[Result])(implicit request: Request[JsValue]) = {
    request.body.validate[T].map(t) match {
      case JsSuccess(result, _) => result
      case JsError(err) => Future.successful(BadRequest(Json.toJson(err.map {
        case (path, errors) => Json.obj("path" -> path.toString, "errors" -> JsArray(errors.flatMap(e => e.messages.map(JsString(_)))))
      })))
    }
  }
//
//  private def parseJsonParam(param: String)(implicit request: Request[Any]): (String, Try[JsValue]) = (param, Try(request.queryString.get(param).map(_.head).map(Json.parse(_)).getOrElse(Json.obj())))
//
//  private def toError(t: (String, Try[JsValue])): JsObject = t match {
//    case (paramName, Failure(e)) => Json.obj(paramName -> e.getMessage)
//    case _ => Json.obj()
//  }
}