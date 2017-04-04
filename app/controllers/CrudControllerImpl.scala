package controllers

import java.util.concurrent.TimeoutException

import dataaccess.{AsyncCrudRepo, Identity, RepoException, User}
import models.{AdaException, Page, Translation}
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import play.api.data.Form

import scala.concurrent.Future
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID

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

  protected def home: Result

  // create view

  protected type CreateViewData = Form[E]

  protected def createView: WebContext => CreateViewData => Html

  private def createViewAux(
    data: CreateViewData)(
    implicit context: WebContext
  ) = createView(context)(data)

  // show view

  override protected type ShowViewData = EditViewData

  override protected def createShowViewData(id: ID, item: E) = createEditViewData(id, item)

  // edit view

  protected type EditViewData = EditViewFormData[ID, E]

  protected def createEditViewData(id: ID, item: E) = Future(EditViewFormData(id, fillForm(item)))

  protected def editView: WebContext => EditViewData => Html

  private def editViewAux(
    data: EditViewData)(
    implicit context: WebContext
  ) = editView(context)(data)

  // list view

  override protected type ListViewData = Page[E]

  override protected def createListViewData(page: Page[E]) = Future(page)

  // actions

  def create = Action { implicit request =>
    Ok(createViewAux(form))
  }

  /**
    * Retrieve a single object by its id and display as editable.
    * NotFound response is generated if key does not exists.
    *
    * @param id id/ primary key of the object.
    */
  def edit(id: ID) = Action.async { implicit request =>
    {
      for {
        // retrieve the item
        item <- repo.get(id)

        // create a view data if the item has been found
        viewData <- item.fold(
          Future(Option.empty[EditViewData])
        ) { entity =>
          createEditViewData(id, entity).map(Some(_))
        }
      } yield
        item match {
          case None => NotFound(s"Entity #$id not found")
          case Some(entity) =>
            render {
              case Accepts.Html() => Ok(editViewAux(viewData.get))
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
        }
    }.recover {
      case e: AdaException =>
        Logger.error("Problem found while executing the edit function")
        BadRequest(e.getMessage)
      case t: TimeoutException =>
        Logger.error("Problem found while executing the edit function")
        InternalServerError(t.getMessage)
    }
  }

  def save = save(home)

  protected def save(redirect: Result) = Action.async { implicit request =>
    formFromRequest.fold(
      { formWithErrors =>
        Future.successful(BadRequest(createViewAux(formWithErrors)))
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
        Future.successful(BadRequest(editViewAux(EditViewFormData(id, formWithErrors))))
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

case class ShowViewFormData[ID, E](id: ID, form: Form[E])

case class EditViewFormData[ID, E](id: ID, form: Form[E])