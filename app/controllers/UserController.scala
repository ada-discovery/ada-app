package controllers

import javax.inject.Inject

import controllers.dataset.{CategoryController, DataSetController, DictionaryController}
import dataaccess.User

import persistence.RepoTypes.{DataSpaceMetaInfoRepo, UserRepo}
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, seq, email, text}
import models.Page
import reactivemongo.bson.BSONObjectID
import services.MailClientProvider
import views.html
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, RequestHeader, Request, Action}
import util.ReflectionUtil.getMethodNames

import scala.concurrent.Future

class UserController @Inject() (
    userRepo: UserRepo,
    mailClientProvider: MailClientProvider,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends CrudControllerImpl[User, BSONObjectID](userRepo) with AdminRestrictedCrudController[BSONObjectID] {

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "email" -> email,
      "roles" -> seq(text),
      "permissions" -> seq(text)
      )(User.apply)(User.unapply))//(SecurityUtil.secureUserApply)(SecurityUtil.secureUserUnapply))

  override protected val home =
    Redirect(routes.UserController.find())

  override protected def createView(f : Form[User])(implicit msg: Messages, request: Request[_]) = {
    val metaInfos = result(dataSpaceMetaInfoRepo.find())
    val dataSetActionNames = getMethodNames[DataSetController]
    val fieldActionNames = getMethodNames[DictionaryController]
    val categoryActionNames = getMethodNames[CategoryController]

    html.user.create(f, metaInfos, dataSetActionNames, fieldActionNames, categoryActionNames)
  }

  override protected def showView(id: BSONObjectID, f : Form[User])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[User])(implicit msg: Messages, request: Request[_]) = {
    // TODO: move to admin
    val metaInfos = result(dataSpaceMetaInfoRepo.find())
    val dataSetActionNames = getMethodNames[DataSetController]
    val fieldActionNames = getMethodNames[DictionaryController]
    val categoryActionNames = getMethodNames[CategoryController]

    html.user.edit(id, f, metaInfos, dataSetActionNames, fieldActionNames, categoryActionNames)
  }

  override protected def listView(currentPage: Page[User])(implicit msg: Messages, request: Request[_]) =
    html.user.list(currentPage)


  override protected def saveCall(item: User)(implicit request: Request[AnyContent]): Future[BSONObjectID] = {
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
}