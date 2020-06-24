package controllers.sampleRequest

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.sampleRequest.SampleRequestSetting
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models._
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.DataSetWebContext
import org.ada.web.services.DataSpaceService
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.controllers._
import org.incal.play.security.AuthAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, BodyParser}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.SampleRequestSettingRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SampleRequestSettingController @Inject()(
  requestSettingRepo: SampleRequestSettingRepo,
  dataSetSettingRepo: DataSetSettingRepo,
  dsaf: DataSetAccessorFactory,
  dataSpaceService: DataSpaceService
) extends AdaCrudControllerImpl[SampleRequestSetting, BSONObjectID](requestSettingRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicListView[SampleRequestSetting]
  with HasBasicFormCreateView[SampleRequestSetting]
  with HasFormShowEqualEditView[SampleRequestSetting, BSONObjectID] {

  override protected lazy val entityName = "Request Setting"

  override protected def formatId(id: BSONObjectID) = id.stringify

  override protected val homeCall = routes.SampleRequestSettingController.listAll()

  private val controllerName = this.getClass.getSimpleName

  private val settingPermission = "EX:" + controllerName.replaceAllLiterally("Controller", "")

  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  private implicit val idFormatter = BSONObjectIDStringFormatter

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "viewId" -> of[BSONObjectID]
    )(SampleRequestSetting.apply)(SampleRequestSetting.unapply)
  )

  override def saveCall(
    requestSetting: SampleRequestSetting)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      requestSettingExists <- repo.find(
        Seq("dataSetId" #== requestSetting.dataSetId),
        limit = Some(1)
      ).map(_.nonEmpty)
      _ = if (requestSettingExists) throw new AdaException(s"A configuration already exists for dataset id '${requestSetting.dataSetId}'.")
      id <- repo.save(requestSetting)
      dataSetSetting <- dataSetSettingRepo.find(
        Seq("dataSetId" #== requestSetting.dataSetId),
        limit = Some(1)
      ).map(_.headOption)
      _ <- {
        val newMenuLink = Link("Sample Request", routes.SampleRequestController.submissionForm(requestSetting.dataSetId).url)

        val newDataSetSetting = dataSetSetting.get.copy(
          extraNavigationItems = dataSetSetting.get.extraNavigationItems :+ newMenuLink
        )
        dataSetSettingRepo.update(newDataSetSetting)
      } if dataSetSetting.isDefined
    } yield
      id

  private val noDataSetRedirect = goHome.flashing("errors" -> "No data set id specified.")

  override def create = AuthAction { implicit request =>
    getDataSetId(request).map( dataSetId =>
      if (dataSetId.trim.nonEmpty)
        super.create(request)
      else
        Future(noDataSetRedirect)
    ).getOrElse(
      Future(noDataSetRedirect)
    )
  }

  override protected def createView = { implicit ctx =>
    val dataSetId = getDataSetId(ctx.request.asInstanceOf[AuthenticatedRequest[AnyContent]])
    if (dataSetId.isEmpty) {
      throw new AdaException("No dataSetId specified.")
    }
    implicit val dataSetWebCtx = dataSetWebContext(dataSetId.get)
    views.html.sampleRequestSetting.create(_)
  }

  private def getDataSetId(request: AuthenticatedRequest[AnyContent]): Option[String] =
    request.queryString.get("dataSetId") match {
      case Some(matches) => Some(matches.head)
      case None => request.body.asFormUrlEncoded.flatMap(_.get("dataSetId").map(_.head))
    }

  override protected type EditViewData =
    (
      BSONObjectID,
      Form[SampleRequestSetting],
      Traversable[DataSpaceMetaInfo],
      DataSetSetting
    )

  override protected def getFormEditViewData(
    requestId: BSONObjectID,
    form: Form[SampleRequestSetting]
  ) = { implicit request =>
    for {
      sampleRequestSettingOption <- repo.get(requestId)
      sampleRequestSetting = sampleRequestSettingOption.getOrElse(
        throw new AdaException(s"No request setting found for the id '${requestId.stringify}'.")
      )
      dsa = dsaf(sampleRequestSetting.dataSetId).getOrElse(
        throw new AdaException(s"No dsa found for the data set id '${sampleRequestSetting.dataSetId}'.")
      )
      dataSetSetting <- dsa.setting
      dataSpaceTree <- dataSpaceService.getTreeForCurrentUser
    } yield {
      (
        requestId,
        form,
        dataSpaceTree,
        dataSetSetting
      )
    }
  }

  override protected def editView = { implicit ctx =>
    data: EditViewData =>
      implicit val dataSetWebCtx = dataSetWebContext(data._4.dataSetId)
      (views.html.sampleRequestSetting.edit(_, _, _, _)).tupled(data)
  }

  override protected def listView = { implicit ctx =>
    (views.html.sampleRequestSetting.list(_, _)).tupled
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
        Json.obj(
          "name" -> metaInfo.id,
          "label" -> metaInfo.id
        )
      }
      Ok(Json.toJson(dataSetIdJsons))
    }
  }

}