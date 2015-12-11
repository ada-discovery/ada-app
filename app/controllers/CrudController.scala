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
 * Generic async entity CRUD controller
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
protected abstract class CrudController[E: Format, ID](
    repo: AsyncCrudRepo[E, ID]
  )(implicit identity: Identity[E, ID]) extends ReadonlyController[E, ID](repo) {

  protected def form : Form[E]

  protected def createView(
    form : Form[E]
  )(implicit msg: Messages, request: RequestHeader) : Html

  protected def editView(
    id : ID,
    form : Form[E]
  )(implicit msg: Messages, request: RequestHeader) : Html

  protected def showView(
     id : ID,
     form : Form[E]
  )(implicit msg: Messages, request: RequestHeader) : Html

  override protected def showView(
    id : ID,
    entity : E
  )(implicit msg: Messages, request: RequestHeader) =
    showView(id, form.fill(entity))

  protected def home : Result

  protected def defaultCreateEntity : E

  def create = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(createView(form.fill(defaultCreateEntity)))
  }

  def edit(id: ID) = Action.async { implicit request =>
//    println(request.tags.get(Router.Tags.RouteController))
//    println(request.tags.get(Router.Tags.RouteActionMethod))

    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ) { entity =>
      implicit val msg = messagesApi.preferred(request)
      Ok(editView(id, form.fill(entity)))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the edit process")
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

//  private def parseJsonParam(param: String)(implicit request: Request[Any]): (String, Try[JsValue]) = (param, Try(request.queryString.get(param).map(_.head).map(Json.parse(_)).getOrElse(Json.obj())))
//
//  private def toError(t: (String, Try[JsValue])): JsObject = t match {
//    case (paramName, Failure(e)) => Json.obj(paramName -> e.getMessage)
//    case _ => Json.obj()
//  }
}