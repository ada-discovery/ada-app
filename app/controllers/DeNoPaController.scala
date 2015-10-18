package controllers

import java.util.concurrent.TimeoutException

import models.Page
import persistence.AsyncReadonlyRepo
import play.api.Logger
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, Flash, Controller, RequestHeader}
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import play.api.libs.concurrent.Execution.Implicits.defaultContext

abstract class DeNoPaController(
    repo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    messagesApi: MessagesApi
  ) extends Controller {

  def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) : Html

  def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) : Html

  def get(id: BSONObjectID) = Action.async { implicit request =>
    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ){ entity =>
      implicit val msg = messagesApi.preferred(request)

      Ok(showView(entity))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the edit process")
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
    val criteria = if (!query.isEmpty)
      Some(Json.obj("Probanden_Nr" -> Json.obj("$regex" -> (query + ".*"), "$options" -> "i")))
    else
      None
    val sort = if (!orderBy.isEmpty)
      Some(Json.obj(orderBy -> 1))
    else
      None

    val futureItems = repo.find(criteria, sort, None, Some(limit), Some(page))
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
}