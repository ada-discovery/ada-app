package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import models.{Page, Translation}
import persistence.RepoTypeRegistry._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{date, ignored, mapping, nonEmptyText}
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, RequestHeader}
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import views.html

import scala.concurrent.duration.DurationInt

class TranslationController @Inject() (
    translationRepo: TranslationRepo,
    messagesApi: MessagesApi
  ) extends CrudController[Translation, BSONObjectID](translationRepo, messagesApi) {

  implicit val timeout = 10.seconds

  /**
   * Describe the user form (used in both edit and create screens).
   */
  override val form = Form(
    mapping(
      "id" -> ignored(Option(BSONObjectID.generate: BSONObjectID)),
      "original" -> nonEmptyText,
      "translated" -> nonEmptyText
    )(Translation.apply)(Translation.unapply))

  override val home =
    Redirect(routes.TranslationController.listAll())

  override def createView(f : Form[Translation])(implicit msg: Messages, request: RequestHeader) =
    html.translation.create(f).asInstanceOf[Html]

  override def editView(id: BSONObjectID, f : Form[Translation])(implicit msg: Messages, request: RequestHeader) =
    html.translation.edit(id, f).asInstanceOf[Html]

  override def listView(currentPage: Page[Translation], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.translation.list(currentPage, currentOrderBy, currentFilter).asInstanceOf[Html]

  override val defaultCreateEntity =
    new Translation(None, null, null)

  /**
   * Display the paginated list of users.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param name Filter applied on user names
   */
  def findByOriginal(page: Int, orderBy: String, name: String) = Action.async { implicit request =>
    val limit = 20
    val criteria = Json.parse("{\"original\":{\"$regex\":\"^" + name + ".*\",\"$options\":\"i\"}}").as[JsObject]
    val sort = Json.obj(orderBy -> 1)

    val futureItems = dao.find(Some(criteria), Some(sort), None, Some(limit), Some(page))
    val futureCount = dao.count(Some(criteria))
    futureItems.zip(futureCount).map({ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(listView(Page(items, page, page * limit, count), orderBy, name))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in user list process")
        InternalServerError(t.getMessage)
    }
  }
}