package controllers.orderrequest

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.BatchOrderRequestSetting
import models.BatchOrderRequestSetting.BatchRequestSettingIdentity
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
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestSettingRepo
import views.html.{requestSettings => requestViews}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BatchOrderRequestSettingController @Inject()(
  requestSettingRepo: BatchOrderRequestSettingRepo,
  dsaf: DataSetAccessorFactory,
  dataSetSettingRepo: DataSetSettingRepo,
  dataSpaceService: DataSpaceService,
  userRepo: UserRepo
) extends AdaCrudControllerImpl[BatchOrderRequestSetting, BSONObjectID](requestSettingRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicListView[BatchOrderRequestSetting]
  with HasBasicFormCreateView[BatchOrderRequestSetting]
  with HasFormShowEqualEditView[BatchOrderRequestSetting, BSONObjectID] {

  override protected val homeCall = routes.BatchOrderRequestSettingController.listAll()

  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  private implicit val idFormatter = BSONObjectIDStringFormatter

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "timeCreated" -> ignored(new Date()),
      "committeeUserIds" -> seq(of[BSONObjectID]),
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
}