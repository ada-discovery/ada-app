package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import controllers.{AdminRestrictedCrudController, SeqFormatter, MapJsonFormatter, CrudControllerImpl}
import models.DataSetFormattersAndIds._
import models.{DataSetSetting, Page}
import persistence.RepoTypes._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, RequestHeader, Request}
import play.api.libs.json.Json
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import services.DataSetService
import views.html
import controllers.dataset.routes.{DataSetSettingController => dataSetSettingRoutes}
import controllers.dataset.routes.javascript.{DataSetSettingController => dataSetSettingJsRoutes}
import play.api.data.Mapping
import controllers.dataset.DataSetSettingController.dataSetSettingMapping

import scala.concurrent.Future

class DataSetSettingController @Inject() (
    repo: DataSetSettingRepo,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetService: DataSetService
  ) extends CrudControllerImpl[DataSetSetting, BSONObjectID](repo) with AdminRestrictedCrudController[BSONObjectID] {

  override protected val form = Form(dataSetSettingMapping)

  override protected val home =
    Redirect(routes.DataSetSettingController.listAll())

  override protected def createView(f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) =
    html.datasetsetting.create(f)

  override protected def showView(id: BSONObjectID, f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) = {
    html.datasetsetting.editNormal(id, "", f)
  }

  override protected def listView(currentPage: Page[DataSetSetting])(implicit msg: Messages, request: Request[_]) =
    html.datasetsetting.list("", currentPage)

  def editForDataSet(dataSet: String) = restrict {
    Action.async { implicit request =>
      val foundSettingFuture = repo.find(Some(Json.obj("dataSetId" -> dataSet))).map(_.headOption)
      foundSettingFuture.map { setting =>
        setting.fold(
          NotFound(s"Setting for the data set '#$dataSet' not found")
        ) { entity =>
          implicit val msg = messagesApi.preferred(request)

          render {
            case Accepts.Html() => {
              val updateCall = dataSetSettingRoutes.updateForDataSet(entity._id.get)
              val cancelCall = new DataSetRouter(dataSet).plainOverviewList
              Ok(html.datasetsetting.edit("", form.fill(entity), updateCall, cancelCall, result(dataSpaceMetaInfoRepo.find())))
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
      dataSetIdFuture.flatMap( dataSetId =>
        update(id, Redirect(new DataSetRouter(dataSetId).plainOverviewList)).apply(request)
      )
    }
  }

  def addToChartOverview(dataSet: String, fieldName: String) = restrict {
    Action.async { implicit request =>
      val foundSettingFuture = repo.find(Some(Json.obj("dataSetId" -> dataSet))).map(_.headOption)
      foundSettingFuture.flatMap {
        _.fold(
          Future(NotFound(s"Setting for the data set '#$dataSet' not found"))
        ) { setting =>
          val newSetting = setting.copy(overviewChartFieldNames = setting.overviewChartFieldNames ++ Seq(fieldName))
          repo.update(newSetting).map(_ => Ok("Done"))
        }
      }
    }
  }

  def jsRoutes = restrict{
    Action { implicit request =>
      Ok(
        JavaScriptReverseRouter("jsRoutes")(
          dataSetSettingJsRoutes.addToChartOverview
        )
      ).as("text/javascript")
    }
  }

  override protected val defaultCreateEntity = new DataSetSetting("")
}

object DataSetSettingController {
  implicit val seqFormatter = SeqFormatter.apply
  implicit val mapFormatter = MapJsonFormatter.apply

  val dataSetSettingMapping: Mapping[DataSetSetting] = mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "dataSetId" -> nonEmptyText,
    "keyFieldName" -> nonEmptyText,
    "exportOrderByFieldName" -> nonEmptyText,
    "listViewTableColumnNames" -> of[Seq[String]],
    "overviewChartFieldNames" -> of[Seq[String]],
    "defaultScatterXFieldName" -> nonEmptyText,
    "defaultScatterYFieldName" -> nonEmptyText,
    "defaultDistributionFieldName" -> nonEmptyText,
    "tranSMARTVisitFieldName" -> optional(text),
    "tranSMARTReplacements" -> default(of[Map[String, String]], Map("\n" -> " ", "\r" -> " "))
  ) (DataSetSetting.apply)(DataSetSetting.unapply)
}