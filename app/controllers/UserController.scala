package controllers

import javax.inject.Inject

import controllers.core.{CrudControllerImpl, HasBasicListView, HasFormShowEqualEditView}
import controllers.dataset._
import dataaccess.User
import dataaccess.RepoTypes.{DataSpaceMetaInfoRepo, UserRepo}
import play.api.data.Form
import play.api.data.Forms.{email, ignored, mapping, nonEmptyText, seq, text}
import models.{DataSpaceMetaInfo, Page}
import reactivemongo.bson.BSONObjectID
import services.MailClientProvider
import views.html.{user => view}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import util.ReflectionUtil.getMethodNames

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserController @Inject() (
    userRepo: UserRepo,
    mailClientProvider: MailClientProvider,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends CrudControllerImpl[User, BSONObjectID](userRepo)

    with AdminRestrictedCrudController[BSONObjectID]
    with HasFormShowEqualEditView[User, BSONObjectID]
    with HasBasicListView[User] {

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "email" -> email,
      "roles" -> seq(text),
      "permissions" -> seq(text)
      )(User.apply)(User.unapply))//(SecurityUtil.secureUserApply)(SecurityUtil.secureUserUnapply))

  override protected val home =
    Redirect(routes.UserController.find())

  private val controllerActionNames = DataSetControllerActionNames(
    getMethodNames[DataSetController],
    getMethodNames[DictionaryController],
    getMethodNames[CategoryController],
    getMethodNames[FilterController],
    getMethodNames[DataViewController]
  )

  // create view and data

  override protected type CreateViewData = (
    Form[User],
    Traversable[DataSpaceMetaInfo],
    DataSetControllerActionNames
  )

  override protected def getFormCreateViewData(form: Form[User]) =
    for {
      all <- dataSpaceMetaInfoRepo.find()
    } yield
      (form, all, controllerActionNames)

  override protected[controllers] def createView = { implicit ctx =>
    (view.create(_,_, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    BSONObjectID,
    Form[User],
    Traversable[DataSpaceMetaInfo],
    DataSetControllerActionNames
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[User]
  ) = { request =>
    for {
      all <- dataSpaceMetaInfoRepo.find()
    } yield
      (id, form, all, controllerActionNames)
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.edit(_, _, _, _)).tupled
  }

  // list view and data

  override protected[controllers] def listView = { implicit ctx => view.list(_) }

  // actions

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

case class DataSetControllerActionNames(
  dataSetActions: Array[String],
  fieldActions: Array[String],
  categoryActions: Array[String],
  filterActions: Array[String],
  dataViewActions: Array[String]
)