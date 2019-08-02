package controllers.requestSetting

import java.util.Date
import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models._
import org.ada.server.AdaException
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.controllers._
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.RequestSettingRepo
import services.UserProviderService
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Deprecated
class RequestSettingController @Inject()(
                                              requestSettingRepo: RequestSettingRepo,
                                              val userManager: UserManager,
                                              userProvider: UserProviderService
                                            ) extends AdaCrudControllerImpl[BatchRequestSetting, BSONObjectID](requestSettingRepo)
  with SubjectPresentRestrictedCrudController[BSONObjectID]
  with HasShowView[BatchRequestSetting, BSONObjectID]
  with HasEditView[BatchRequestSetting, BSONObjectID]
  with HasBasicListView[BatchRequestSetting]
  with HasBasicFormCreateView[BatchRequestSetting]
  with AdaAuthConfig {

  private implicit val idsFormatter = BSONObjectIDStringFormatter

  override protected val homeCall = {
    routes.RequestSettingController.listAll()
  }

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "timeCreated" -> ignored(new Date()),
      "userIds" -> seq(of[BSONObjectID]),
      "displayFieldNames" -> seq(nonEmptyText)
    )(BatchRequestSetting.apply)(BatchRequestSetting.unapply))

  override def get(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminAny(noCaching = true) {
      toAuthenticatedAction(super.get(id))
    }

  override def edit(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminAny(noCaching = true) {
      toAuthenticatedAction(super.edit(id))
    }

  override def update(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.update(id)))

  override def delete(id: BSONObjectID): Action[AnyContent] =
    restrictAdminAny(noCaching = true) {
      toAuthenticatedAction(super.delete(id))
    }

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] =
    restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.find(page, orderBy, filter)))

  override def listAll(orderBy: String): Action[AnyContent] = {
    restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.listAll(orderBy)))
  }

  override protected type EditViewData = (
    IdForm[BSONObjectID, BatchRequestSetting],  Map[BSONObjectID, String]
    )

  override protected type ShowViewData = (
    BatchRequestSetting, Traversable[String]
    )

  override protected def getFormEditViewData(requestId: BSONObjectID, form: Form[BatchRequestSetting]): AuthenticatedRequest[_] => Future[EditViewData]  = {
    implicit request => {
      for {
        existingSetting <- repo.get(requestId)
        users <- userProvider.getUsersByIds(existingSetting.get.userIds.map(Some(_)))
      } yield {
        (IdForm(requestId, form), users.map(u=>(u._1,u._2.ldapDn)))
      }
    }
  }

  override protected def getFormShowViewData(requestId: BSONObjectID, form: Form[BatchRequestSetting]): AuthenticatedRequest[_] => Future[ShowViewData]  = {
    implicit request => {
      for {
        existingSetting <- repo.get(requestId)
        users <- userProvider.getUsersByIds(existingSetting.get.userIds.map(Some(_)))
      } yield {
        (form.get, users.map(_._2.ldapDn))
      }
    }
  }

  override def saveCall(
                         requestSetting: BatchRequestSetting)(
                         implicit request: AuthenticatedRequest[AnyContent]
                       ): Future[BSONObjectID] = {
    for {
      dataSetIdExists <- repo.find(Seq("dataSetId" #== requestSetting.dataSetId))
      id <- {
        dataSetIdExists.size == 0 match {
          case false => throw new AdaException("A configuration already exists for dataset id " + requestSetting.dataSetId)
          case true => repo.save(requestSetting)
        }
      }
    } yield {
      id
    }
  }

  override protected def createView = { implicit ctx => views.html.requestSettings.create(_) }

  override protected def showView = { implicit ctx =>
    (views.html.requestSettings.show(_, _)).tupled
  }

  override protected def editView = { implicit ctx =>
    (views.html.requestSettings.edit(_, _)).tupled
  }

  override protected def listView = { implicit ctx =>
    (views.html.requestSettings.list(_, _)).tupled
  }
}