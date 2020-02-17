package controllers.orderrequest

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.BatchOrderRequestSetting
import models.BatchOrderRequestSetting.BatchRequestSettingIdentity
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, DataSpaceMetaInfoRepo, FieldRepo, UserRepo}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.DataSetFormattersAndIds._
import org.ada.server.models.User.UserIdentity
import org.ada.server.models._
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.DataSetWebContext
import org.ada.web.services.DataSpaceService
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.controllers._
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, BodyParser}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import services.BatchOrderRequestRepoTypes.BatchOrderRequestSettingRepo
import views.html.{requestSettings => requestViews}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BatchOrderRequestSettingController @Inject()(
  requestSettingRepo: BatchOrderRequestSettingRepo,
  dsaf: DataSetAccessorFactory,
  dataSetSettingRepo: DataSetSettingRepo,
  dataSpaceService: DataSpaceService,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
  userRepo: UserRepo
) extends AdaCrudControllerImpl[BatchOrderRequestSetting, BSONObjectID](requestSettingRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicListView[BatchOrderRequestSetting]
  with HasBasicFormCreateView[BatchOrderRequestSetting]
  with HasFormShowEqualEditView[BatchOrderRequestSetting, BSONObjectID] {

//  override protected val entityNameKey = "batchOrderRequestSetting"

  override protected lazy val entityName = "Request Setting"

  override protected def formatId(id: BSONObjectID) = id.stringify

  override protected val homeCall = routes.BatchOrderRequestSettingController.listAll()

  private val controllerName = this.getClass.getSimpleName

  private val settingPermission = "EX:" + controllerName.replaceAllLiterally("Controller", "")

  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  private implicit val idFormatter = BSONObjectIDStringFormatter

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "timeCreated" -> ignored(new Date()),
      "committeeUserIds" -> seq(of[BSONObjectID]).verifying("At least one committee member needs to be specified.", _.nonEmpty),
      "viewId" -> of[BSONObjectID]
    )(BatchOrderRequestSetting.apply)(BatchOrderRequestSetting.unapply)
  )

  // Save
  override def saveCall(
    requestSetting: BatchOrderRequestSetting)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      // check if a request setting for a given data set id exists
      requestSettingExists <- repo.find(
        Seq("dataSetId" #== requestSetting.dataSetId),
        limit = Some(1)
      ).map(_.headOption.isDefined)

      _ = if (requestSettingExists)
        throw new AdaException(s"A configuration already exists for dataset id '${requestSetting.dataSetId}'.")

      // save the request setting
      id <- repo.save(requestSetting)

      // get the associated data set setting
      dataSetSetting <- dataSetSettingRepo.find(
        Seq("dataSetId" #== requestSetting.dataSetId),
        limit = Some(1)
      ).map(_.headOption)

      // add a handy link to a new order request screen
      _ <- {
        val newMenuLink = Link("Order Request", routes.BatchOrderRequestController.createNew(requestSetting.dataSetId).url)

        val newDataSetSetting = dataSetSetting.get.copy(
          extraNavigationItems = dataSetSetting.get.extraNavigationItems :+ newMenuLink
        )
        dataSetSettingRepo.update(newDataSetSetting)
      } if (dataSetSetting.isDefined)
    } yield
      id

  // Create
  override def create = AuthAction { implicit request =>
    getDataSetId(request) match {
      case Some(dataSetId) if dataSetId.trim.nonEmpty => super.create(request)
      case _ => Future(goHome.flashing("errors" -> "No data set id specified."))
    }
  }

  override protected def createView = { implicit ctx =>
    val dataSetId = getDataSetId(ctx.request.asInstanceOf[AuthenticatedRequest[AnyContent]])
    if (dataSetId.isEmpty) {
      throw new AdaException("No dataSetId specified.")
    }
    implicit val dataSetWebCtx = dataSetWebContext(dataSetId.get)
    views.html.requestSettings.create(_)
  }

  private def getDataSetId(request: AuthenticatedRequest[AnyContent]): Option[String] =
    request.queryString.get("dataSetId") match {
      case Some(matches) => Some(matches.head)
      case None => request.body.asFormUrlEncoded.flatMap(_.get("dataSetId").map(_.head))
    }

  // Edit
  override protected type EditViewData = (
    BSONObjectID,
    Form[BatchOrderRequestSetting],
    Map[BSONObjectID, User],
    Traversable[DataSpaceMetaInfo],
    DataSetSetting
  )

  override protected def getFormEditViewData(
    requestId: BSONObjectID,
    form: Form[BatchOrderRequestSetting]
  ) = { implicit request =>
    for {
      // get a batch request setting
      existingSetting <- repo.get(requestId)

      // check is exists
      _ = require(existingSetting.isDefined, s"No request setting found for the id '${requestId.stringify}'.")

      // get the associated data set id
      dataSetId = existingSetting.get.dataSetId

      // create a data set accessor
      dsa = dsaf(dataSetId).getOrElse(
        throw new AdaException(s"No dsa found for the data set id '${dataSetId}'.")
      )

      // data set setting
      dataSetSetting <- dsa.setting

      // get the reference users/committee members
      refUsers <- userRepo.find(Seq(UserIdentity.name #-> existingSetting.get.committeeUserIds.map(Some(_))))

      // data space tree
      dataSpaceTree <- dataSpaceService.getTreeForCurrentUser
    } yield {
      val idUserMap = refUsers.map(user => (user._id.get, user)).toMap

      (
        requestId,
        form,
        idUserMap,
        dataSpaceTree,
        dataSetSetting
      )
    }
  }

  override protected def editView = { implicit ctx =>
    data: EditViewData =>
      implicit val dataSetWebCtx = dataSetWebContext(data._5.dataSetId)
      (requestViews.edit(_, _, _, _, _)).tupled(data)
  }

  // List
  override protected def listView = { implicit ctx =>
    (views.html.requestSettings.list(_, _)).tupled
  }

  // Changing admin-restricted to admin-or-permission restricted
  override protected def restrict[A](
    bodyParser: BodyParser[A]
  ) = restrictAdminOrPermission[A](settingPermission, bodyParser, noCaching = true)

  def dataSetIds = restrictAny { implicit request =>
    for {
      dataSetMetaInfos <- dataSpaceService.getDataSetMetaInfosForCurrentUser
    } yield {
      val dataSetIdJsons = dataSetMetaInfos.map { metaInfo =>
        Json.obj("name" -> metaInfo.id , "label" -> metaInfo.id)
      }
      Ok(Json.toJson(dataSetIdJsons))
    }
  }

  def userIdAndNames = restrictAny { implicit request =>
    for {
      users <- userRepo.find()
    } yield {
      val idAndNames = users.toSeq.map( user =>
        Json.obj("_id" -> user._id, "name" -> user.ldapDn)
      )
      Ok(Json.toJson(idAndNames))
    }
  }

  def addAdmin = restrictAny { implicit request =>
    {
      val userId = request.body.asFormUrlEncoded.flatMap { form =>
        form.get("userId").flatMap(params => BSONObjectID.parse(params.head).toOption)
      }

      userId match {
        case Some(userId) =>
          for {
            user <- userRepo.get(userId)

            // update the user (if found)
            _ <- if (user.isDefined && !user.get.permissions.contains(settingPermission))
              userRepo.update(user.get.copy(permissions = user.get.permissions ++ Seq(settingPermission)))
            else
              Future(())
          } yield {
            user match {
              case Some(user) => goHome.flashing("success" -> s"User '${user.ldapDn}' was made a batch-order request admin.")
              case None => goHome.flashing("errors" -> s"No user '${userId.stringify}' found.")
            }
          }

        case None =>
          Future(goHome.flashing("errors" -> s"No user id specified."))
      }
    }.recover(handleExceptions("an add-admin"))
  }
}