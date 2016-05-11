package controllers

import javax.inject.Inject

import models.security.{SecurityRoleCache, SecurityPermissionCache, CustomUser}
import persistence.MailClientProvider

import persistence.RepoTypes.UserRepo
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, seq, email, text}
import models.Page
import reactivemongo.bson.BSONObjectID
import views.html
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, RequestHeader, Request}

import util.SecurityUtil

import scala.concurrent.Future

class UserController @Inject() (
    userRepo: UserRepo,
    mailClientProvider: MailClientProvider
  ) extends CrudController[CustomUser, BSONObjectID](userRepo) {

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "email" -> email,
      "roles" -> seq(text),
      "permissions" -> seq(text)
      )(CustomUser.apply)(CustomUser.unapply))//(SecurityUtil.secureUserApply)(SecurityUtil.secureUserUnapply))

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


  override protected def saveCall(item: CustomUser)(implicit request: Request[AnyContent]): Future[BSONObjectID] = {
    val mailer = mailClientProvider.createClient()
    val mail = mailClientProvider.createTemplate(
      "Ucer Created",
      Seq(item.email),
      bodyText = Some("A new user account has been created." + System.lineSeparator() +
        "You can now log into the Ada Reporting System with this mail address.")
    )
    mailer.send(mail)
    repo.save(item)
  }

  //@Deprecated
  override protected val defaultCreateEntity = new CustomUser(None, "", "", Seq[String](), Seq[String]())
  //override protected val defaultCreateEntity = new CustomUser(None, "", "", "", "", Seq(SecurityRoleCache.basicRole), Seq())
}