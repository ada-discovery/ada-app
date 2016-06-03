package controllers

import javax.inject.Inject

import controllers.dataset.{CategoryController, DataSetController, DictionaryController}
import models.security.CustomUser
import persistence.MailClientProvider

import persistence.RepoTypes.{DataSpaceMetaInfoRepo, UserRepo}
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, seq, email, text}
import models.{DataSpaceMetaInfo, Page}
import reactivemongo.bson.BSONObjectID
import views.html
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, RequestHeader, Request, Action}
import util.ReflectionUtil.getMethodNames

import scala.concurrent.Future

class UserController @Inject() (
    userRepo: UserRepo,
    mailClientProvider: MailClientProvider,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
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

  override protected def createView(f : Form[CustomUser])(implicit msg: Messages, request: RequestHeader) = {
    val metaInfos = result(dataSpaceMetaInfoRepo.find())
    val dataSetActionNames = getMethodNames[DataSetController]
    val fieldActionNames = getMethodNames[DictionaryController]
    val categoryActionNames = getMethodNames[CategoryController]

    html.user.create(f, metaInfos, dataSetActionNames, fieldActionNames, categoryActionNames)
  }

  override protected def showView(id: BSONObjectID, f : Form[CustomUser])(implicit msg: Messages, request: RequestHeader) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[CustomUser])(implicit msg: Messages, request: RequestHeader) = {
    // TODO: move to admin
    val metaInfos = result(dataSpaceMetaInfoRepo.find())
    val dataSetActionNames = getMethodNames[DataSetController]
    val fieldActionNames = getMethodNames[DictionaryController]
    val categoryActionNames = getMethodNames[CategoryController]

    html.user.edit(id, f, metaInfos, dataSetActionNames, fieldActionNames, categoryActionNames)
  }

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