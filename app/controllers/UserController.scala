package controllers

import javax.inject.Inject

import models.User
import persistence.RepoTypeRegistry.UserRepo
import play.api.data.Form
import play.api.data.Forms.{date, ignored, mapping, nonEmptyText}
import models.Page
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import reactivemongo.bson.BSONObjectID
import views.html
import play.api.i18n.Messages
import play.api.mvc.RequestHeader

class UserController @Inject() (
    userRepo: UserRepo
  ) extends CrudController[User, BSONObjectID](userRepo) {

  override protected val form = Form(
    mapping(
      "id" -> ignored(BSONObjectID.generate: BSONObjectID),
      "name" -> nonEmptyText,
      "address" -> nonEmptyText,
      "dob" -> date("yyyy-MM-dd"),
      "joiningDate" -> date("yyyy-MM-dd"),
      "designation" -> nonEmptyText)(User.apply)(User.unapply))

  override protected val home =
    Redirect(routes.UserController.listAll())

  override protected def createView(f : Form[User])(implicit msg: Messages, request: RequestHeader) =
    html.user.create(f)

  override protected def showView(id: BSONObjectID, f : Form[User])(implicit msg: Messages, request: RequestHeader) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[User])(implicit msg: Messages, request: RequestHeader) =
    html.user.edit(id, f)

  override protected def listView(currentPage: Page[User])(implicit msg: Messages, request: RequestHeader) =
    html.user.list(currentPage)

  override protected def toJsonCriteria(string : String) =
    if (!string.isEmpty)
      Some(Json.obj("name" -> Json.obj("$regex" -> (string + ".*"), "$options" -> "i")))
    else
      None

  override protected val defaultCreateEntity =
    new User(null, null, null, null, null, null)
}