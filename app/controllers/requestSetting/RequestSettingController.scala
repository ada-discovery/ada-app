package controllers.requestSetting

import java.util.Date

import javax.inject.Inject
import models._
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.incal.play.controllers._
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.RequestSettingRepo

@Deprecated
class RequestSettingController @Inject()(
                                              requestSettingRepo: RequestSettingRepo,
                                              val userManager: UserManager
                                            ) extends AdaCrudControllerImpl[BatchRequestSetting, BSONObjectID](requestSettingRepo)
  with SubjectPresentRestrictedCrudController[BSONObjectID]
  with HasBasicFormCreateView[BatchRequestSetting]
  with HasBasicFormEditView[BatchRequestSetting, BSONObjectID]
  with HasBasicFormShowView[BatchRequestSetting, BSONObjectID]
  with HasBasicListView[BatchRequestSetting]
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



  override protected def createView = { implicit ctx => views.html.requestSettings.create(_) }

  override protected def showView = { implicit ctx =>
    views.html.requestSettings.show(_)
  }

  override protected def editView = { implicit ctx =>
    views.html.requestSettings.edit(_)
  }

  override protected def listView = { implicit ctx =>
    (views.html.requestSettings.list(_, _)).tupled
  }
}