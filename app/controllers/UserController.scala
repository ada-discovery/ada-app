package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import models.User
import persistence.UserRepo
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{date, ignored, mapping, nonEmptyText}
import play.api.i18n.MessagesApi
import models.Page
import play.api.libs.json.{JsObject, Json}
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import views.html
import play.api.i18n.Messages
import play.api.mvc.{Action, Flash, RequestHeader}

import scala.concurrent.duration.DurationInt

class UserController @Inject() (
    userRepo: UserRepo,
    messagesApi: MessagesApi
  ) extends CrudController[User, BSONObjectID](userRepo, messagesApi) {

  implicit val timeout = 10.seconds

  /**
   * Describe the user form (used in both edit and create screens).
   */
  override val form = Form(
    mapping(
      "id" -> ignored(BSONObjectID.generate: BSONObjectID),
      "name" -> nonEmptyText,
      "address" -> nonEmptyText,
      "dob" -> date("yyyy-MM-dd"),
      "joiningDate" -> date("yyyy-MM-dd"),
      "designation" -> nonEmptyText)(User.apply)(User.unapply))

  override val home =
    Redirect(routes.UserController.listAll())

  override def createView(f : Form[User])(implicit msg: Messages, request: RequestHeader) =
    html.user.create(f).asInstanceOf[Html]

  override def editView(id: BSONObjectID, f : Form[User])(implicit msg: Messages, request: RequestHeader) =
    html.user.edit(id, f).asInstanceOf[Html]

  override def listView(currentPage: Page[User], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.user.list(currentPage, currentOrderBy, currentFilter).asInstanceOf[Html]

  override val defaultCreateEntity =
    new User(null, null, null, null, null, null)

  /**
   * Display the paginated list of users.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param name Filter applied on user names
   */
  def findByName(page: Int, orderBy: String, name: String) = Action.async { implicit request =>
    val limit = 5
    val criteria = Json.parse("{\"name\":{\"$regex\":\"^" + name + ".*\",\"$options\":\"i\"}}").as[JsObject]
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