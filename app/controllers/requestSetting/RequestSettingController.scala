package controllers.requestSetting

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models._
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.Field
import org.ada.server.models.User.UserIdentity
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.DataSetWebContext
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.controllers._
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.RequestSettingRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Deprecated
class RequestSettingController @Inject()(
    requestSettingRepo: RequestSettingRepo,
    val userManager: UserManager,
    dsaf: DataSetAccessorFactory,
    userRepo: UserRepo
) extends AdaCrudControllerImpl[BatchRequestSetting, BSONObjectID](requestSettingRepo)
    with SubjectPresentRestrictedCrudController[BSONObjectID]
    with HasShowView[BatchRequestSetting, BSONObjectID]
    with HasEditView[BatchRequestSetting, BSONObjectID]
    with HasBasicListView[BatchRequestSetting]
    with HasBasicFormCreateView[BatchRequestSetting] {

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
        )(BatchRequestSetting.apply)(BatchRequestSetting.unapply)
    )

    override def get(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
        restrictAdminAny(noCaching = true) {
            toAuthenticatedAction(super.get(id))
        }

    private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

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
        IdForm[BSONObjectID, BatchRequestSetting],
            String,
            Traversable[Field]
        )

    override protected type ShowViewData = (
        BatchRequestSetting, Traversable[String]
        )

    private def getUsersByIds(userIds: Traversable[Option[BSONObjectID]]) = {
        userRepo.find(Seq(UserIdentity.name #-> userIds.toSeq)).map { users =>
            users.map(c => (c._id.get, c)).toMap
        }
    }

    private def getUsers() = {
        userRepo.find().map { users =>
            users.map(c => (c._id.get, c)).toMap
        }
    }

    override protected def getFormEditViewData(requestId: BSONObjectID, form: Form[BatchRequestSetting]): AuthenticatedRequest[_] => Future[EditViewData] = {
        implicit request => {

            for {
                existingSetting <- repo.get(requestId)
                fieldRepo = dsaf(existingSetting.get.dataSetId).get.fieldRepo
                users <- getUsersByIds(existingSetting.get.userIds.map(Some(_)))
                fields <- Future.sequence(existingSetting.get.displayFieldNames.map(fieldRepo.get))
            } yield {

                (IdForm(requestId, form), existingSetting.get.dataSetId, fields.flatten)
            }
        }
    }

    override protected def getFormShowViewData(requestId: BSONObjectID, form: Form[BatchRequestSetting]): AuthenticatedRequest[_] => Future[ShowViewData] = {
        implicit request => {
            for {
                existingSetting <- repo.get(requestId)
                users <- getUsersByIds(existingSetting.get.userIds.map(Some(_)))
            } yield {
                (form.get, users.map(_._2.ldapDn))
            }
        }
    }

    override def saveCall(
        requestSetting: BatchRequestSetting
    )(
        implicit request: AuthenticatedRequest[AnyContent]
    ): Future[BSONObjectID] =
        for {
            dataSetIdExists <- repo.find(Seq("dataSetId" #== requestSetting.dataSetId))

            id <-
            if (dataSetIdExists.size == 0) {
                repo.save(requestSetting)
            } else {
                throw new AdaException("A configuration already exists for dataset id " + requestSetting.dataSetId)
            }
        } yield
            id

    override protected def createView = { implicit ctx =>
        views.html.requestSettings.create(_)
    }

    override protected def showView = { implicit ctx =>
        (views.html.requestSettings.show(_, _)).tupled
    }

    override protected def editView = { implicit ctx => {
        data: (IdForm[BSONObjectID, BatchRequestSetting], String, Traversable[Field]) => {
            implicit val dataSetWebCtx = dataSetWebContext(data._2)
            views.html.requestSettings.edit(data._1)
        }
    }
    }

    override protected def listView = { implicit ctx =>
        (views.html.requestSettings.list(_, _)).tupled
    }
}