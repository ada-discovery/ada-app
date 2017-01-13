package controllers

import java.util.concurrent.TimeoutException
import dataaccess.{Identity, RepoException, AsyncCrudRepo}
import models.AdaException
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import play.api.data.Form

import scala.concurrent.Future
import play.twirl.api.Html

trait CrudController[ID] extends ReadonlyController[ID] {

  def create: Action[AnyContent]

  def edit(id: ID): Action[AnyContent]

  def save: Action[AnyContent]

  def update(id: ID): Action[AnyContent]

  def delete(id: ID): Action[AnyContent]
}

/**
 * Generic async entity CRUD controller
 *
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
protected abstract class CrudControllerImpl[E: Format, ID](
    override val repo: AsyncCrudRepo[E, ID]
  )(implicit identity: Identity[E, ID]) extends ReadonlyControllerImpl[E, ID] with CrudController[ID] {

  protected def form: Form[E]

  protected def fillForm(entity: E): Form[E] =
    form.fill(entity)

  protected def formFromRequest(implicit request: Request[AnyContent]): Form[E] =
    form.bindFromRequest

  protected def createView(
    form : Form[E]
  )(implicit msg: Messages, request: Request[_]): Html

  protected def editView(
    id : ID,
    form : Form[E]
  )(implicit msg: Messages, request: Request[_]): Html

  protected def showView(
     id : ID,
     form : Form[E]
  )(implicit msg: Messages, request: Request[_]): Html

  override protected def showView(
    id : ID,
    entity : E
  )(implicit msg: Messages, request: Request[_]) =
    showView(id, fillForm(entity))

  protected def home: Result

  def create = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(createView(form))
  }

  def edit(id: ID) = Action.async { implicit request =>
    editCall(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ) { entity =>
      implicit val msg = messagesApi.preferred(request)

      render {
        case Accepts.Html() => Ok(editView(id, fillForm(entity)))
        case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
      }
    }).recover {
      case e: AdaException =>
        Logger.error("Problem found while executing the edit function")
        BadRequest(e.getMessage)
      case t: TimeoutException =>
        Logger.error("Problem found while executing the edit function")
        InternalServerError(t.getMessage)
    }
  }

  protected def editCall(id: ID)(implicit request: Request[AnyContent]): Future[Option[E]] = repo.get(id)

  def save = save(home)

  protected def save(redirect: Result) = Action.async { implicit request =>
    formFromRequest.fold(
      { formWithErrors =>
        implicit val msg = messagesApi.preferred(request)
        Future.successful(BadRequest(createView(formWithErrors)))
      },
      item => {
        saveCall(item).map { id =>
          render {
            case Accepts.Html() => redirect.flashing("success" -> s"Item ${id} has been created")
            case Accepts.Json() => Created(Json.obj("message" -> "Item successfully created", "id" -> id.toString))
          }
        }.recover {
          case e: AdaException =>
            Logger.error("Problem found while executing the save function")
            BadRequest(e.getMessage)
          case t: TimeoutException =>
            Logger.error("Problem found while executing the save function")
            InternalServerError(t.getMessage)
          case i: RepoException =>
            Logger.error("Problem found while executing the save function")
            InternalServerError(i.getMessage)
        }
      }
    )
  }

  protected def saveCall(item: E)(implicit request: Request[AnyContent]): Future[ID] = repo.save(item)

  def update(id: ID): Action[AnyContent] = update(id, home)

  protected def update(id: ID, redirect: Result): Action[AnyContent] = Action.async { implicit request =>
    formFromRequest.fold(
      { formWithErrors =>
        implicit val msg = messagesApi.preferred(request)
        Future.successful(BadRequest(editView(id, formWithErrors)))
      },
      item => {
        updateCall(identity.set(item, id)).map { _ =>
          render {
            case Accepts.Html() => redirect.flashing("success" -> s"Item ${id} has been updated")
            case Accepts.Json() => Ok(Json.obj("message" -> "Item successly updated", "id" -> id.toString))
          }
        }.recover {
          case e: AdaException =>
            Logger.error("Problem found while executing the update function")
            BadRequest(e.getMessage)
          case t: TimeoutException =>
            Logger.error("Problem found while executing the update function")
            InternalServerError(t.getMessage)
          case i: RepoException =>
            Logger.error("Problem found while executing the update function")
            InternalServerError(i.getMessage)
        }
      })
  }

  protected def updateCall(item: E)(implicit request: Request[AnyContent]): Future[ID] = repo.update(item)

  def delete(id: ID) = Action.async { implicit request =>
    deleteCall(id).map { _ =>
      render {
        case Accepts.Html() => home.flashing("success" -> s"Item ${id} has been deleted")
        case Accepts.Json() => Ok(Json.obj("message" -> "Item successly deleted", "id" -> id.toString))
      }
    }.recover {
      case e: AdaException =>
        Logger.error(s"Problem deleting the item ${id}")
        BadRequest(e.getMessage)
      case t: TimeoutException =>
        Logger.error(s"Problem deleting the item ${id}")
        InternalServerError(t.getMessage)
      case i: RepoException =>
        Logger.error(s"Problem deleting the item ${id}")
        InternalServerError(i.getMessage)
    }
  }

  protected def deleteCall(id: ID)(implicit request: Request[AnyContent]): Future[Unit] = repo.delete(id)

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