package controllers.requestSetting

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.BatchRequestSetting
import models.BatchRequestSetting.BatchRequestSettingIdentity
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{FieldRepo, UserRepo}
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.models.DataSetFormattersAndIds._
import org.ada.server.models.User.UserIdentity
import org.ada.server.models._
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.DataSetWebContext
import org.ada.web.services.DataSpaceService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.controllers._
import org.incal.play.formatters.JsonFormatter
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.json.{JsArray, Json}
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
    dataSpaceService: DataSpaceService,
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

    private implicit val widgetSpecFormatter = JsonFormatter[WidgetSpec]
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

    def copyBatch(
        sourceDataSetId: String,
        targetDataSetIds: Seq[String]
    ) =
        restrictAdminAny(noCaching = true) {
            implicit request => {
                for {
                    sourceSetting <- repo.find(Seq("dataSetId" #== sourceDataSetId))
                    targetSettingsExistingOptions <- repo.find(Seq("dataSetId" #-> targetDataSetIds))
                    deleteExisting <- repo.delete(targetSettingsExistingOptions.map(_._id.get))
                    ids <- Future.sequence(targetDataSetIds.map(
                        dataSetId => {
                            val requestSetting = BatchRequestSetting(dataSetId = dataSetId, userIds = sourceSetting.head.userIds, widgetSpecs = Seq(), displayFieldNames = sourceSetting.head.displayFieldNames)
                            repo.save(requestSetting)
                        }
                    )
                    )
                } yield {
                    Redirect(homeCall).flashing("success" -> "state of request updated with success")
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

    def copy = restrictAdminAny(noCaching = true) {
        implicit request => {
            Future {
                render {
                    case Accepts.Html() => Ok(views.html.requestSettings.copy())
                    case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
                }
            }
        }
    }

    def getDataSetIdsWithRequestSettings = restrictAdminAny(noCaching = true) {
        implicit request =>
            for {
                requestSettings <- repo.find()
            } yield {
                val dataSetNames = requestSettings.map(setting =>
                    Json.obj("name" -> setting.dataSetId, "label" -> setting.dataSetId)
                )
                Ok(JsArray(dataSetNames.toSeq))
            }
    }

    override protected def getFormEditViewData(requestId: BSONObjectID, form: Form[BatchRequestSetting]): AuthenticatedRequest[_] => Future[EditViewData] = {
        implicit request => {
            val dataSet = form.get.dataSetId
            dsaf(dataSet).map { dsa =>
                val filtersFuture =
                    form.value match {
                        case Some(dataView) =>
                            val filterIds = dataView.widgetSpecs.map(_.subFilterId).flatten
                            if (filterIds.nonEmpty) {
                                dsa.filterRepo.find(
                                    projection = Seq("name")
                                )
                            } else
                                Future(Nil)
                        case None => Future(Nil)
                    }

                val dataSetNameFuture = dsa.dataSetName
                val nameFieldMapFuture = getNameFieldMap(dsa.fieldRepo)
                val settingFuture = dsa.setting


                for {
                    existingSetting <- repo.get(requestId)
                    fieldRepo = dsaf(existingSetting.get.dataSetId).get.fieldRepo
                    users <- getUsersByIds(existingSetting.get.userIds.map(Some(_)))
                    fields <- Future.sequence(existingSetting.get.displayFieldNames.map(fieldRepo.get))
                    (dataSetName, dataSpaceTree, dataSetSetting) <- getDataSetNameTreeAndSetting(dsa)
                    setting <- requestSettingRepo.find(Seq("dataSetId" #== dataSet)).map(_.headOption)
                    filters <- filtersFuture
                    nameFieldMap <- nameFieldMapFuture
                    dataSetName <- dataSetNameFuture
                    dataSetSetting <- settingFuture
                } yield {
                    val idFilterNameMap = filters.map(filter => (filter._id.get, filter.name.getOrElse(""))).toMap
                    (IdForm(requestId, form), existingSetting.get.dataSetId, nameFieldMap, idFilterNameMap, dataSetSetting.filterShowFieldStyle, dataSpaceTree, dataSetSetting)
                }
            }.get
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

    private def getUsersByIds(userIds: Traversable[Option[BSONObjectID]]) = {
        userRepo.find(Seq(UserIdentity.name #-> userIds.toSeq)).map { users =>
            users.map(c => (c._id.get, c)).toMap
        }
    }

    override protected type CreateViewData = (
        Form[BatchRequestSetting]
        )

    private def getDataSetNameTreeAndSetting(
        dsa: DataSetAccessor
    )(
        implicit request: AuthenticatedRequest[_]
    ): Future[(String, Traversable[DataSpaceMetaInfo], DataSetSetting)] = {
        val dataSetNameFuture = dsa.dataSetName
        val treeFuture = dataSpaceService.getTreeForCurrentUser
        val settingFuture = dsa.setting

        for {
            dataSetName <- dataSetNameFuture
            dataSpaceTree <- treeFuture
            setting <- settingFuture
        } yield
            (dataSetName, dataSpaceTree, setting)
    }

    private def getNameFieldMap(fieldRepo: FieldRepo): Future[Map[String, Field]] =
        fieldRepo.find().map {
            _.map(field =>
                (field.name, field)
            ).toMap
        }

    override protected type EditViewData = (
        IdForm[BSONObjectID, BatchRequestSetting],
            String,
            Map[String, Field],
            Map[BSONObjectID, String],
            Option[FilterShowFieldStyle.Value],
            Traversable[DataSpaceMetaInfo],
            DataSetSetting
        )

    override protected type ShowViewData = (
        BatchRequestSetting, Traversable[String]
        )

    override protected def showView = { implicit ctx =>
        (views.html.requestSettings.show(_, _)).tupled
    }

    override protected def editView = { implicit ctx => {
        data: EditViewData => {
            implicit val dataSetWebCtx = dataSetWebContext(data._2)
            views.html.requestSettings.edit(data._1, data._3, data._4, data._5, data._6, data._7)
        }
    }
    }

    private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

    override protected def listView = { implicit ctx =>
        (views.html.requestSettings.list(_, _)).tupled
    }

    private def getUsers() = {
        userRepo.find().map { users =>
            users.map(c => (c._id.get, c)).toMap
        }
    }

    def getDatasetOptionsByGroup(dataGroupId: String) = restrictAdminAny(noCaching = true) {
        implicit request =>
            for {
                groupDataSetIds <- Future {
                    Seq("car.car", "sar.sap")
                }
            } yield {
                val dataSetNames = groupDataSetIds.map(id =>
                    Json.obj("name" -> id, "label" -> id)
                )
                Ok(JsArray(dataSetNames))
            }
    }

    override protected def createView = { implicit ctx =>
        views.html.requestSettings.create(_)
    }

    def getDataGroups = restrictAdminAny(noCaching = true) {
        implicit request =>
            for {
                dataGroups <- Future {
                    Seq("group 1", "group 2")
                }
            } yield {
                val dataGroupNames = dataGroups.map(id =>
                    Json.obj("name" -> id, "label" -> id)
                )
                Ok(JsArray(dataGroupNames))
            }
    }
}