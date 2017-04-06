package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import controllers._
import dataaccess.{AscSort, Criterion}
import dataaccess.RepoTypes.{DataSetSettingRepo, DataSpaceMetaInfoRepo}
import models.{ChartType, DataSetFormattersAndIds, DataSetSetting, FieldChartType}
import models.DataSetFormattersAndIds.{DataSetSettingIdentity, fieldChartTypeFormat, serializableDataSetSettingFormat, widgetSpecFormat}
import models._
import models.FilterShowFieldStyle
import Criterion.Infix
import controllers.core.{CrudControllerImpl, HasBasicFormCreateView, HasBasicListView, HasFormShowEqualEditView}
import persistence.dataset.{DataSetAccessorFactory, DataSpaceMetaInfoRepo}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import services.DataSetService
import views.html.{category, datasetsetting => view}
import controllers.dataset.routes.{DataSetSettingController => dataSetSettingRoutes}
import controllers.dataset.routes.javascript.{DataSetSettingController => dataSetSettingJsRoutes}

import scala.concurrent.Future
import scala.reflect.ClassTag

class DataSetSettingController @Inject() (
    repo: DataSetSettingRepo,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetService: DataSetService,
    dsaf: DataSetAccessorFactory
  ) extends CrudControllerImpl[DataSetSetting, BSONObjectID](repo)

    with AdminRestrictedCrudController[BSONObjectID]
    with HasBasicFormCreateView[DataSetSetting]
    with HasFormShowEqualEditView[DataSetSetting, BSONObjectID]
    with HasBasicListView[DataSetSetting] {

  private implicit val chartTypeFormatter = EnumFormatter(ChartType)

  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)
  private implicit val storageTypeFormatter = EnumFormatter(StorageType)
  private implicit val widgetSpecFormatter = JsonFormatter[WidgetSpec]

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "keyFieldName" -> nonEmptyText,
      "exportOrderByFieldName" -> optional(text),
      "defaultScatterXFieldName" -> optional(text),
      "defaultScatterYFieldName" -> optional(text),
      "defaultDistributionFieldName" -> nonEmptyText,
      "defaultCumulativeCountFieldName" -> optional(text),
      "filterShowFieldStyle" -> optional(of[FilterShowFieldStyle.Value]),
      "tranSMARTVisitFieldName" -> optional(text),
      "tranSMARTReplacements" -> default(of[Map[String, String]], Map("\n" -> " ", "\r" -> " ")),
      "storageType" -> of[StorageType.Value],
      "mongoAutoCreateIndexForProjection" -> boolean,
      "cacheDataSet" -> boolean
    )(DataSetSetting.apply)(DataSetSetting.unapply)
  )

  override protected val home =
    Redirect(routes.DataSetSettingController.find())

  // create view

  override protected def createView = { implicit ctx => view.create(_) }

  // edit view and data (show view = edit view)

  override protected type EditViewData = (
    BSONObjectID,
    Form[DataSetSetting],
    Call
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[DataSetSetting]
  ): Future[EditViewData] = {
    val settingFuture = form.value match {
      case Some(setting) => Future(Some(setting))
      case None => repo.get(id)
    }

    for {
      // get the setting if not provided
      setting <- settingFuture
    } yield {
      val newForm = form.copy(value = setting)
      val fieldNamesCall = new DataSetRouter(setting.get.dataSetId).fieldNames
      (id, newForm, fieldNamesCall)
    }
  }

  override protected def editView = { implicit ctx =>
    (view.editNormal(_, _, _)).tupled
  }

  // list view

  override protected def listView = { implicit ctx => view.list(_) }

  def editForDataSet(dataSet: String) = restrict {
    Action.async { implicit request =>
      val foundSettingFuture = repo.find(Seq("dataSetId" #== dataSet)).map(_.headOption)
      foundSettingFuture.map { setting =>
        setting.fold(
          NotFound(s"Setting for the data set '#$dataSet' not found")
        ) { entity =>
          implicit val msg = messagesApi.preferred(request)
          val fieldNamesCall = new DataSetRouter(dataSet).fieldNames

          render {
            case Accepts.Html() => {
              val updateCall = dataSetSettingRoutes.updateForDataSet(entity._id.get)
              val cancelCall = new DataSetRouter(dataSet).getDefaultView
              Ok(view.edit(
                fillForm(entity),
                updateCall,
                cancelCall,
                fieldNamesCall,
                result(DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)))
              )
            }
            case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
          }
        }
      }.recover {
        case t: TimeoutException =>
          Logger.error("Problem found in the edit process")
          InternalServerError(t.getMessage)
      }
    }
  }

  def updateForDataSet(id: BSONObjectID) = restrict {
    Action.async { implicit request =>
      val dataSetIdFuture = repo.get(id).map(_.get.dataSetId)
      dataSetIdFuture.flatMap { dataSetId =>
        update(id, Redirect(new DataSetRouter(dataSetId).getDefaultView)).apply(request)
      }
    }
  }

  override protected def updateCall(
    item: DataSetSetting)(
    implicit request: Request[AnyContent]
  ): Future[BSONObjectID] = {
    repo.update(item).map { id =>
      // update data set repo since we change the setting, which could affect how the data set is accessed
      dsaf(item.dataSetId).foreach(_.updateDataSetRepo(item))
      // return id
      id
    }
  }
}