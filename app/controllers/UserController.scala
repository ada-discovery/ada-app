package controllers

import javax.inject.Inject

import controllers.dataset._
import dataaccess.User

import dataaccess.RepoTypes.{UserRepo, DataSpaceMetaInfoRepo}
import persistence.dataset.DataSpaceMetaInfoRepo
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, seq, email, text}
import models.Page
import reactivemongo.bson.BSONObjectID
import services.MailClientProvider
import views.html.{user => view}
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

  override protected def createView = { implicit ctx =>
    data =>
      val metaInfos = result(DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo))
      val dataSetActionNames = getMethodNames[DataSetController]
      val fieldActionNames = getMethodNames[DictionaryController]
      val categoryActionNames = getMethodNames[CategoryController]
      val filterActionNames = getMethodNames[FilterController]
      val dataViewActionNames = getMethodNames[DataViewController]

      view.create(
        data,
        metaInfos,
        dataSetActionNames,
        fieldActionNames,
        categoryActionNames,
        filterActionNames,
        dataViewActionNames
      )
  }

  override protected def showView = editView

  override protected def editView = { implicit ctx =>
    data =>
      // TODO: move to admin
      val metaInfos = result(DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo))
      val dataSetActionNames = getMethodNames[DataSetController]
      val fieldActionNames = getMethodNames[DictionaryController]
      val categoryActionNames = getMethodNames[CategoryController]
      val filterActionNames = getMethodNames[FilterController]
      val dataViewActionNames = getMethodNames[DataViewController]

      view.edit(
        data.id,
        data.form,
        metaInfos,
        dataSetActionNames,
        fieldActionNames,
        categoryActionNames,
        filterActionNames,
        dataViewActionNames
      )
  }

  override protected def listView = { implicit ctx => view.list(_) }

  override protected def saveCall(
    item: User)(
    implicit request: Request[AnyContent]
  ): Future[BSONObjectID] = {
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