package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import controllers.{SeqFormatter, MapJsonFormatter, CrudController}
import models.DataSetFormattersAndIds._
import models.{DataSetSetting, Page}
import persistence.RepoTypes._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Action, RequestHeader}
import play.api.libs.json.Json
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import views.html
import controllers.dataset.routes.{DataSetSettingController => dataSetSettingRoutes}

class DataSetSettingController @Inject() (repo: DataSetSettingRepo) extends CrudController[DataSetSetting, BSONObjectID](repo) {

  implicit val mapFormatter = MapJsonFormatter.apply
  implicit val seqFormatter = SeqFormatter.apply

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "keyFieldName" -> nonEmptyText,
      "exportOrderByFieldName" -> nonEmptyText,
      "listViewTableColumnNames" -> of[Seq[String]],
      "overviewChartFieldNames" -> of[Seq[String]],
      "defaultScatterXFieldName" -> nonEmptyText,
      "defaultScatterYFieldName" -> nonEmptyText,
      "defaultDistributionFieldName" -> nonEmptyText,
      "tranSMARTVisitFieldName" -> optional(nonEmptyText),
      "tranSMARTReplacements" -> of[Map[String, String]]
    ) (DataSetSetting.apply)(DataSetSetting.unapply))

  override protected val home =
    Redirect(routes.DataSetSettingController.listAll())

  override protected def createView(f : Form[DataSetSetting])(implicit msg: Messages, request: RequestHeader) =
    html.datasetsetting.create(f)

  override protected def showView(id: BSONObjectID, f : Form[DataSetSetting])(implicit msg: Messages, request: RequestHeader) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[DataSetSetting])(implicit msg: Messages, request: RequestHeader) = {
    html.datasetsetting.editNormal(id, "", f)
  }

  override protected def listView(currentPage: Page[DataSetSetting])(implicit msg: Messages, request: RequestHeader) =
    html.datasetsetting.list("", currentPage)

  def editForDataSet(dataSetId: String) = Action.async { implicit request =>
    val foundSettingFuture = repo.find(Some(Json.obj("dataSetId" -> dataSetId))).map(_.headOption)
    foundSettingFuture.map { setting =>
      setting.fold(
        NotFound(s"Setting for the data set '#$dataSetId' not found")
      ) { entity =>
        implicit val msg = messagesApi.preferred(request)

        render {
          case Accepts.Html() => {
            val updateCall = dataSetSettingRoutes.updateForDataSet(entity._id.get)
            val cancelCall = new DataSetRouter(dataSetId).plainOverviewList
            Ok(html.datasetsetting.edit("", form.fill(entity), updateCall, cancelCall))
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

  def updateForDataSet(id: BSONObjectID) = Action.async { implicit request =>
    val dataSetIdFuture = repo.get(id).map(_.get.dataSetId)
    dataSetIdFuture.flatMap( dataSetId =>
      update(id, Redirect(new DataSetRouter(dataSetId).plainOverviewList)).apply(request)
    )
  }

  //@Deprecated
  override protected val defaultCreateEntity = new DataSetSetting("")
}