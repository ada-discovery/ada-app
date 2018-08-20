package controllers.core

import java.util.concurrent.TimeoutException

import dataaccess.{AsyncCrudRepo, Identity, RepoException}
import models.AdaException
import play.api.Logger
import play.api.data.Form
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.Future

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
protected[controllers] abstract class CrudControllerImpl[E: Format, ID](
    override val repo: AsyncCrudRepo[E, ID]
  )(implicit identity: Identity[E, ID]) extends ReadonlyControllerImpl[E, ID]
    with CrudController[ID]
    with HasFormCreateView[E]
    with HasFormShowView[E, ID]
    with HasFormEditView[E, ID] {

  protected def fillForm(entity: E): Form[E] =
    form.fill(entity)

  protected def formFromRequest(implicit request: Request[AnyContent]): Form[E] =
    form.bindFromRequest

  protected def home: Result

  // actions

  def create = AuthAction { implicit request =>
    getCreateViewData.map(viewData =>
      Ok(createViewWithContext(viewData))
    )
  }

  /**
    * Retrieve a single object by its id and display as editable.
    * NotFound response is generated if key does not exists.
    *
    * @param id id/ primary key of the object.
    */
  def edit(id: ID) = AuthAction { implicit request =>
    {
      for {
        // retrieve the item
        item <- repo.get(id)

        // create a view data if the item has been found
        viewData <- item.fold(
          Future(Option.empty[EditViewData])
        ) { entity =>
          getEditViewData(id, entity)(request).map(Some(_))
        }
      } yield
        item match {
          case None => NotFound(s"$entityName '${formatId(id)}' not found")
          case Some(entity) =>
            render {
              case Accepts.Html() => Ok(editViewWithContext(viewData.get))
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

  protected def save(redirect: Result) = AuthAction { implicit request =>
    formFromRequest.fold(
      { formWithErrors =>
        getFormCreateViewData(formWithErrors).map(viewData =>
          BadRequest(createViewWithContext(viewData))
        )
      },
      item => {
        saveCall(item).map { id =>
          render {
            case Accepts.Html() => redirect.flashing("success" -> s"$entityName '${formatId(id)}' has been created")
            case Accepts.Json() => Created(Json.obj("message" -> s"$entityName successfully created", "id" -> formatId(id)))
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

  protected def update(id: ID, redirect: Result): Action[AnyContent] = AuthAction { implicit request =>
    formFromRequest.fold(
      { formWithErrors =>
        getFormEditViewData(id, formWithErrors)(request).map { viewData =>
          BadRequest(editViewWithContext(viewData))
        }
      },
      item => {
        updateCall(identity.set(item, id)).map { _ =>
          render {
            case Accepts.Html() => redirect.flashing("success" -> s"$entityName '${formatId(id)}' has been updated")
            case Accepts.Json() => Ok(Json.obj("message" -> s"$entityName successly updated", "id" -> formatId(id)))
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

  def delete(id: ID) = AuthAction { implicit request =>
    deleteCall(id).map { _ =>
      render {
        case Accepts.Html() => home.flashing("success" -> s"$entityName '${formatId(id)}' has been deleted")
        case Accepts.Json() => Ok(Json.obj("message" -> s"$entityName successfully deleted", "id" -> formatId(id)))
      }
    }.recover {
      case e: AdaException =>
        Logger.error(s"Problem deleting the item '${formatId(id)}'")
        BadRequest(e.getMessage)
      case t: TimeoutException =>
        Logger.error(s"Problem deleting the item '${formatId(id)}'")
        InternalServerError(t.getMessage)
      case i: RepoException =>
        Logger.error(s"Problem deleting the item '${formatId(id)}'")
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