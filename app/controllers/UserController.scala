package controllers

import javax.inject.Inject

import controllers.core.AdaCrudControllerImpl
import controllers.dataset._
import dataaccess.RepoTypes.{DataSpaceMetaInfoRepo, UserRepo}
import play.api.data.Form
import play.api.data.Forms.{email, ignored, mapping, nonEmptyText, seq, text}
import models.{DataSpaceMetaInfo, User}
import org.incal.core.dataaccess.AscSort
import reactivemongo.bson.BSONObjectID
import services.MailClientProvider
import views.html.{user => view}
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import org.incal.core.util.ReflectionUtil.getMethodNames
import org.incal.play.Page
import org.incal.play.controllers.{AdminRestrictedCrudController, CrudControllerImpl, HasBasicListView, HasFormShowEqualEditView}
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.restrictAdminAnyNoCaching

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserController @Inject() (
    userRepo: UserRepo,
    mailClientProvider: MailClientProvider,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends AdaCrudControllerImpl[User, BSONObjectID](userRepo)
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

  override protected val homeCall = routes.UserController.find()

  private val controllerActionNames = DataSetControllerActionNames(
    getMethodNames[DataSetController],
    getMethodNames[DictionaryController],
    getMethodNames[CategoryController],
    getMethodNames[FilterController],
    getMethodNames[DataViewController],
    getMethodNames[StandardClassificationRunController],
    getMethodNames[StandardRegressionRunController]
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

  override protected def listView = { implicit ctx =>
    (view.list(_, _)).tupled
  }

  // actions

  override protected def saveCall(
    user: User)(
    implicit request: Request[AnyContent]
  ): Future[BSONObjectID] = {

    // send an email
    val mailer = mailClientProvider.createClient()
    val mail = mailClientProvider.createTemplate(
      "User Created",
      Seq(user.email),
      bodyText = Some("A new user account has been created." + System.lineSeparator() +
        "You can now log into the Ada Reporting System with this mail address.")
    )
    mailer.send(mail)

    // remove repeated permissions
    val userToSave = user.copy(permissions = user.permissions.toSet.toSeq.sorted)

    super.saveCall(userToSave)
  }

  override protected def updateCall(
    user: User)(
    implicit request: Request[AnyContent]
  ): Future[BSONObjectID] = {
    // remove repeated permissions
    val userToUpdate = user.copy(permissions = user.permissions.toSet.toSeq.sorted)

    super.updateCall(userToUpdate)
  }

  def listUsersForPermissionPrefix(
    permissionPrefix: Option[String]
  ) = restrictAdminAnyNoCaching(deadbolt) { implicit request =>
    for {
      allUsers <- repo.find(sort = Seq(AscSort("ldapDn")))
    } yield {
      val filteredUsers = if (permissionPrefix.isDefined)
        allUsers.filter(_.permissions.exists(_.startsWith(permissionPrefix.get)))
      else
        allUsers
      val page = Page(filteredUsers, 0, 0, filteredUsers.size, "ldapDn")
      Ok(view.list(page, Nil))
    }
  }

  def copyPermissions(
    sourceUserId: BSONObjectID,
    targetUserId: BSONObjectID
  ) = restrictAdminAnyNoCaching(deadbolt) { implicit request =>
    for {
      sourceUser <- repo.get(sourceUserId)
      targetUser <- repo.get(targetUserId)

      userId <- {
        (sourceUser, targetUser).zipped.headOption.map { case (user1, user2) =>
          val userWithMergedPermissions = user2.copy(permissions = user2.permissions ++ user1.permissions)
          repo.update(userWithMergedPermissions).map(Some(_))
        }.getOrElse(
          Future(None)
        )
      }
    } yield
      userId.map { _ =>
        Redirect(homeCall).flashing("success" -> "Permissions successfully copied.")
      }.getOrElse(
        BadRequest(s"User '${sourceUserId.stringify}' or '${targetUserId.stringify}' not found.")
      )
  }
}

case class DataSetControllerActionNames(
  dataSetActions: Traversable[String],
  fieldActions: Traversable[String],
  categoryActions: Traversable[String],
  filterActions: Traversable[String],
  dataViewActions: Traversable[String],
  classificationRunActions: Traversable[String],
  regressionRunActions: Traversable[String]
)

object UserDataSetPermissions {

  val viewOnly = Seq(
    "dataSet.getView",
    "dataSet.getDefaultView",
    "dataSet.getWidgets",
    "dataSet.getViewElementsAndWidgetsCallback",
    "dataSet.getNewFilterViewElementsAndWidgetsCallback",
    "dataSet.generateTable",
    "dataSet.getFieldNamesAndLabels",
    "dataSet.getFieldTypeWithAllowedValues",
    "dataSet.getCategoriesWithFieldsAsTreeNodes",
    "dataSet.getFieldValue",
    "dataview.idAndNamesAccessible",
    "filter.idAndNamesAccessible"
  )

  val standard = Seq(
    "dataSet",
    "field.find",
    "category.idAndNames",
    "dataview",
    "filter"
  )
}