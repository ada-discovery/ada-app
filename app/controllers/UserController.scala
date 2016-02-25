package controllers

import javax.inject.Inject

import models.security.{SecurityRoleCache, SecurityPermissionCache, CustomUser}

import persistence.RepoTypeRegistry.UserRepo
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, seq, email, text}
import models.Page
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import reactivemongo.bson.BSONObjectID
import views.html
import play.api.i18n.Messages
import play.api.mvc.RequestHeader

class UserController @Inject() (
    userRepo: UserRepo
  ) extends CrudController[CustomUser, BSONObjectID](userRepo) {

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "email" -> email,
      "password" -> nonEmptyText,
      "affiliation" -> text,
      "roleNames" -> seq(text),
      "permissionNames" -> seq(text)
      )(CustomUser.apply)(CustomUser.unapply))

  override protected val home =
    Redirect(routes.UserController.listAll())

  override protected def createView(f : Form[CustomUser])(implicit msg: Messages, request: RequestHeader) =
    html.user.create(f)

  override protected def showView(id: BSONObjectID, f : Form[CustomUser])(implicit msg: Messages, request: RequestHeader) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[CustomUser])(implicit msg: Messages, request: RequestHeader) =
    html.user.edit(id, f)

  override protected def listView(currentPage: Page[CustomUser])(implicit msg: Messages, request: RequestHeader) =
    html.user.list(currentPage)

  //@Deprecated
  override protected val defaultCreateEntity = new CustomUser(None, "", "", "", "", List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions)
}