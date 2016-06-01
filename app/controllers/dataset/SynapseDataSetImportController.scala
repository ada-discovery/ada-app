package controllers.dataset

import javax.inject.Inject

import controllers.dataset.DataSetSettingController.dataSetSettingMapping
import models.{AdaException, AdaParseException, SynapseDataSetImportInfo}
import persistence.RepoTypes._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, RequestHeader, Result}
import services.DataSetService

import scala.concurrent.Future

class SynapseDataSetImportController @Inject()(
    repo: DataSetSettingRepo,
    dataSetService: DataSetService,
    messagesApi: MessagesApi
  ) extends Controller {

  private val logger = Logger // (this.getClass)

  protected val form = Form(
    mapping(
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "tableId" -> nonEmptyText,
      "setting" -> optional(dataSetSettingMapping)
    ) (SynapseDataSetImportInfo.apply)(SynapseDataSetImportInfo.unapply)
  )

  private val defaultImportInfo =
    SynapseDataSetImportInfo("", "", "", "", None)

  def create = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(views.html.dataset.importSynapseDataSet(form.fill(defaultImportInfo)))
  }

  def upload = Action.async { implicit request =>
    val filledForm = form.bindFromRequest
    filledForm.fold(
      { formWithErrors =>
        Future.successful(createBadRequest(formWithErrors))
      },
      importInfo => {
        val dataSetName = importInfo.dataSetName
        val errorRedirect = handleError(filledForm, dataSetName) _
        val successRedirect = Redirect(new DataSetRouter(importInfo.dataSetId).plainOverviewList)
        dataSetService.importDataSet(importInfo).map { _ =>
          render {
            case Accepts.Html() => successRedirect.flashing("success" -> s"Data set '$dataSetName' has been uploaded.")
            case Accepts.Json() => Created(Json.obj("message" -> "Data set has been uploaded", "name" -> importInfo.dataSetName))
          }
        }.recover {
          case e: AdaParseException => errorRedirect(s"Parsing problem occured: '${e.getMessage}'")
          case e: AdaException => errorRedirect(e.getMessage)
          case e: Exception => errorRedirect(s"Fatal problem detected: '${e.getMessage}'. Contact your admin.")
        }
      }
    )
  }

  private def handleError(
    filledForm: Form[SynapseDataSetImportInfo],
    dataSetName: String)(
    message: String
  )(implicit request: RequestHeader): Result = {
    logger.error(message)
    createBadRequest(filledForm.withGlobalError(s"Data set '${dataSetName}' upload failed. $message"))
  }

  private def createBadRequest(
    filledForm: Form[SynapseDataSetImportInfo]
  )(implicit request: RequestHeader) = {
    implicit val msg = messagesApi.preferred(request)
    BadRequest(views.html.dataset.importSynapseDataSet(filledForm))
  }
}