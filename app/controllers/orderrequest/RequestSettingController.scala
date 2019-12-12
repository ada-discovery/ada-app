package controllers.orderrequest

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.orderrequest.routes
import javax.inject.Inject
import models.BatchRequestSetting
import models.BatchRequestSetting.BatchRequestSettingIdentity
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, FieldRepo, UserRepo}
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
import org.incal.play.formatters.JsonFormatter
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.RequestSettingRepo
import views.html.{requestSettings => requestViews}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RequestSettingController @Inject()(
  requestSettingRepo: RequestSettingRepo,
  dsaf: DataSetAccessorFactory,
  dataSetSettingRepo: DataSetSettingRepo,
  dataSpaceService: DataSpaceService,
  userRepo: UserRepo
) extends AdaCrudControllerImpl[BatchRequestSetting, BSONObjectID](requestSettingRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicListView[BatchRequestSetting]
  with HasBasicFormCreateView[BatchRequestSetting] {

  override protected val homeCall = routes.RequestSettingController.listAll()

  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  private implicit val widgetSpecFormatter = JsonFormatter[WidgetSpec]
  private implicit val idFormatter = BSONObjectIDStringFormatter

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "timeCreated" -> ignored(new Date()),
      "widgetSpecs" -> seq(of[WidgetSpec]),
      "userIds" -> seq(of[BSONObjectID]),
      "displayFieldNames" -> seq(nonEmptyText)
    )(BatchRequestSetting.apply)(BatchRequestSetting.unapply)
  )

  // Save
  override def saveCall(
    requestSetting: BatchRequestSetting)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      dataSetIdExists <- repo.find(Seq("dataSetId" #== requestSetting.dataSetId), limit = Some(1)).map(_.headOption.isDefined)

      dataSetSetting <- dataSetSettingRepo.find(Seq("dataSetId" #== requestSetting.dataSetId), limit = Some(1)).map(_.headOption)

      id <- if (!dataSetIdExists)
        for {
          id <- repo.save(requestSetting)
          _ <- dataSetSetting.map { dataSetSetting =>
            val newDataSetSetting = dataSetSetting.copy(
              extraNavigationItems = dataSetSetting.extraNavigationItems :+ Link("Order Request", routes.BatchOrderRequestController.createNew(requestSetting.dataSetId).url))
            dataSetSettingRepo.update(newDataSetSetting)
          }.getOrElse(Future(()))
        } yield
          id
      else
        throw new AdaException(s"A configuration already exists for dataset id '${requestSetting.dataSetId}'.")
    } yield
      id

  // Create
  override protected def createView = { implicit ctx =>
    val dataSetId = ctx.request.queryString.get("dataSetId").map(_.head).getOrElse(
      throw new AdaException("No dataSetId specified.")
    )

    implicit val dataSetWebCtx = dataSetWebContext(dataSetId)
    views.html.requestSettings.create(_)
  }

  // Edit
  override protected type EditViewData = (
    BSONObjectID,
    Form[BatchRequestSetting],
    Map[String, Field],
    Map[BSONObjectID, User],
    Map[BSONObjectID, String],
    Traversable[DataSpaceMetaInfo],
    DataSetSetting
  )

  override protected def getFormEditViewData(
    requestId: BSONObjectID,
    form: Form[BatchRequestSetting]
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

      // get all the filters
      filters <- dsa.filterRepo.find(projection = Seq("name"))

      // get the reference users/committee members
      refUsers <- userRepo.find(Seq(UserIdentity.name #-> existingSetting.get.userIds.map(Some(_))))

      // + other auxiliary data
      dataSpaceTree <- dataSpaceService.getTreeForCurrentUser
      fields <- dsa.fieldRepo.find()
      dataSetSetting <- dsa.setting
    } yield {
      val nameFieldMap = fields.map(field => (field.name, field)).toMap

      val idFilterNameMap = filters.map(filter => (filter._id.get, filter.name.getOrElse(""))).toMap
      val idUserMap = refUsers.map(user => (user._id.get, user)).toMap

      (
        requestId,
        form,
        nameFieldMap,
        idUserMap,
        idFilterNameMap,
        dataSpaceTree,
        dataSetSetting
      )
    }
  }

  override protected def editView = { implicit ctx =>
    data: EditViewData =>
      implicit val dataSetWebCtx = dataSetWebContext(data._7.dataSetId)
      (requestViews.edit(_, _, _, _, _, _, _)).tupled(data)
  }

  // Show
  override protected type ShowViewData = (
    BatchRequestSetting,
    Traversable[String]
  )

  override protected def getFormShowViewData(
    requestId: BSONObjectID,
    form: Form[BatchRequestSetting]
  ) = { implicit request =>
    for {
      existingSetting <- repo.get(requestId)

      // check is exists
      _ = require(existingSetting.isDefined, s"No request setting found for the id '${requestId.stringify}'.")

      userIds = existingSetting.get.userIds.map(Some(_))
      users <- userRepo.find(Seq(UserIdentity.name #-> userIds.toSeq))
    } yield
      (form.get, users.map(_.ldapDn))
  }

  override protected def showView = { implicit ctx =>
    (views.html.requestSettings.show(_, _)).tupled
  }

  // List
  override protected def listView = { implicit ctx =>
    (views.html.requestSettings.list(_, _)).tupled
  }
}