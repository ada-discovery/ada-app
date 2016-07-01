package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import controllers._
import models.DataSetFormattersAndIds._
import models._
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
import controllers.dataset.DataSetRouter
import play.api.data.Mapping

import scala.concurrent.Future

class DataSetSettingController @Inject() (
    repo: DataSetSettingRepo,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetService: DataSetService
  ) extends CrudControllerImpl[DataSetSetting, BSONObjectID](repo) with AdminRestrictedCrudController[BSONObjectID] {

  private implicit val chartTypeFormatter = EnumFormatter(ChartType)

//  private val fieldChartMapping: Mapping[FieldChartType] = mapping(
//    "fieldName" -> nonEmptyText,
//    "chartType" -> optional(of[ChartType.Value])
//  ) (FieldChartType.apply)(FieldChartType.unapply)

  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val fieldChartTypeFormatter = JsonFormatter[FieldChartType]

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "keyFieldName" -> nonEmptyText,
      "exportOrderByFieldName" -> nonEmptyText,
      "listViewTableColumnNames" -> seq(text),
      "overviewFieldChartTypes" -> seq(of[FieldChartType]), // fieldChartMapping
      "overviewChartElementGridWidth" -> number(min = 1, max = 12),
      "defaultScatterXFieldName" -> nonEmptyText,
      "defaultScatterYFieldName" -> nonEmptyText,
      "defaultDistributionFieldName" -> nonEmptyText,
      "tranSMARTVisitFieldName" -> optional(text),
      "tranSMARTReplacements" -> default(of[Map[String, String]], Map("\n" -> " ", "\r" -> " "))
    ) (DataSetSetting.apply)(DataSetSetting.unapply)
  )

  override protected val home =
    Redirect(routes.DataSetSettingController.find())

  override protected def createView(f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) =
    html.datasetsetting.create(f)

  override protected def showView(id: BSONObjectID, f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) = {
    val fieldNamesCall = new DataSetRouter(f.value.get.dataSetId).fieldNames
    html.datasetsetting.editNormal(id, "", f, fieldNamesCall)
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
          val fieldNamesCall = new DataSetRouter(dataSet).fieldNames

          render {
            case Accepts.Html() => {
              val updateCall = dataSetSettingRoutes.updateForDataSet(entity._id.get)
              val cancelCall = new DataSetRouter(dataSet).plainOverviewList
              Ok(html.datasetsetting.edit("", fillForm(entity), updateCall, cancelCall, fieldNamesCall, result(dataSpaceMetaInfoRepo.find())))
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

  def addFieldsToChartOverview(dataSet: String, fieldNames: Seq[String]) = restrict {
    processSetting({ setting =>
      val existingFieldCharts = setting.overviewFieldChartTypes
      val existingFieldNames = existingFieldCharts.map(_.fieldName)
      val filteredFieldNames = fieldNames.filter(!existingFieldNames.contains(_))
      if (filteredFieldNames.nonEmpty) {
        val newSetting = setting.copy(overviewFieldChartTypes = existingFieldCharts ++ filteredFieldNames.map(FieldChartType(_, None)))
        repo.update(newSetting)
      } else
        Future(())
    }, dataSet)
  }

  def addFieldsToOverviewTable(dataSet: String, fieldNames: Seq[String]) = restrict {
    processSetting({ setting =>
      val existingFieldNames = setting.listViewTableColumnNames
      val filteredFieldNames = fieldNames.filter(!existingFieldNames.contains(_))
      if (filteredFieldNames.nonEmpty) {
        val newSetting = setting.copy(listViewTableColumnNames = existingFieldNames ++ filteredFieldNames)
        repo.update(newSetting)
      } else {
        Future(())
      }
    }, dataSet)
  }

  protected def processSetting(fun: DataSetSetting => Future[_], dataSet: String) =
    Action.async { implicit request =>
      val foundSettingFuture = repo.find(Some(Json.obj("dataSetId" -> dataSet))).map(_.headOption)
      foundSettingFuture.flatMap {
        _.fold(
          Future(NotFound(s"Setting for the data set '#$dataSet' not found"))
        ) { setting =>
          fun(setting).map(_ => Ok("Done"))
        }
      }
    }

  def jsRoutes = restrict{
    Action { implicit request =>
      Ok(
        JavaScriptReverseRouter("jsRoutes")(
          dataSetSettingJsRoutes.addFieldsToChartOverview,
          dataSetSettingJsRoutes.addFieldsToOverviewTable
        )
      ).as("text/javascript")
    }
  }
}