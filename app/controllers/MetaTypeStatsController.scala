package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import models.{MetaTypeStats, Page}
import persistence.{AsyncReadonlyRepo}
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{Action, Controller}
import play.api.i18n.Messages
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import play.api.mvc.{Action, Flash, RequestHeader}

protected abstract class MetaTypeStatsController(repo: AsyncReadonlyRepo[MetaTypeStats, BSONObjectID]) extends Controller {

  @Inject var messagesApi: MessagesApi = _

  protected def showView(
    item : MetaTypeStats)(
    implicit msg: Messages, request: RequestHeader
  ) : Html

  protected def listView(
    currentPage: Page[MetaTypeStats],
    currentSearchField : String)(
    implicit msg: Messages, request: RequestHeader
  ) : Html

  def get(id: BSONObjectID) = Action.async { implicit request =>
    repo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ){ entity =>
      implicit val msg = messagesApi.preferred(request)
      if (entity.nullRatio > 0) {
        val entityWithNull = entity.copy(valueRatioMap = entity.valueRatioMap.updated("null", entity.nullRatio))
        Ok(showView(entityWithNull))
      } else
        Ok(showView(entity))
    }).recover {
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
   * @param query Filter applied on items
   */
  def find(page: Int, orderBy: String, query: String, searchField : String) = Action.async { implicit request =>
    val limit = 20
    val criteria = if (!query.isEmpty)
      if (searchField == "attributeName")
        Some(Json.obj(searchField -> Json.obj("$regex" -> (query + ".*"), "$options" -> "i")))
      else
        try {
          Some(Json.obj(searchField -> query.toDouble))
        } catch {
          case e : NumberFormatException => None
        }
    else
      None
    val sort = Json.obj(orderBy -> 1)

    val futureItems = repo.find(criteria, Some(sort), None, Some(limit), Some(page))
    val futureCount = repo.count(criteria)
    futureItems.zip(futureCount).map({ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, page, page * limit, count, orderBy, if (criteria.isDefined) query else ""), searchField))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }
}