package controllers.dataset

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import models.{CsvDataSetImportInfo, DataSetSetting, AdaException, AdaParseException}
import persistence.RepoTypes._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, Action, Controller, Result, Request}
import controllers.dataset.DataSetSettingController.dataSetSettingMapping
import services.DataSetService
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.SecurityUtil.restrictAdmin
import views.html.dataset.importinfo.{importCsvDataSet => importView}

import scala.concurrent.Future

class CsvDataSetImportController @Inject()(
    repo: DataSetSettingRepo,
    dataSetService: DataSetService,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi
  ) extends Controller {

  private val logger = Logger // (this.getClass)

  protected val form = Form(
    mapping(
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "path" -> optional(text),
      "delimiter" -> nonEmptyText,
      "eol" -> optional(text),
      "charsetName" -> optional(text),
      "setting" -> optional(dataSetSettingMapping)
    ) (CsvDataSetImportInfo.apply)
      { importInfo: CsvDataSetImportInfo =>
        Some(
          importInfo.dataSpaceName, importInfo.dataSetId,
          importInfo.dataSpaceName, importInfo.path, importInfo.delimiter,
          importInfo.eol, importInfo.charsetName, importInfo.setting
        )
      }
  )

  private val defaultImportInfo =
    CsvDataSetImportInfo("", "", "", None, ",", None, None, None)

  def create = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)
      Ok(importView(form.fill(defaultImportInfo)))
    }
  }

  def upload = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      val filledForm = form.bindFromRequest
      filledForm.fold(
        { formWithErrors =>
          Future.successful(createBadRequest(formWithErrors))
        },
        importInfo => {
          val dataSetName = importInfo.dataSetName
          val importFile = request.body.asMultipartFormData.get.file("importFile")
          val importInfoExt = if (importFile.isDefined) {
            val fileName = importFile.get.filename
            val contentType = importFile.get.contentType
            val file = importFile.get.ref.file
            importInfo.copy(file = Some(file))
          } else
            importInfo

          if (importInfoExt.path.isEmpty && importInfoExt.file.isEmpty)
            Future.successful(createBadRequest(filledForm.withError("path", "No path or import file specified.")))
          else {
            val errorRedirect = handleError(filledForm, dataSetName) _
            val successRedirect = Redirect(new DataSetRouter(importInfo.dataSetId).plainOverviewList)
            dataSetService.importDataSet(importInfoExt).map { _ =>
              render {
                case Accepts.Html() => successRedirect.flashing("success" -> s"Data set '$dataSetName' has been uploaded.")
                case Accepts.Json() => Created(Json.obj("message" -> "Data set has been uploaded", "name" -> importInfo.dataSetName))
              }
            }.recover{
              case e: AdaParseException => errorRedirect(s"Parsing problem occured: '${e.getMessage}'")
              case e: AdaException => errorRedirect(e.getMessage)
              case e: Exception => errorRedirect(s"Fatal problem detected: '${e.getMessage}'. Contact your admin.")
            }
          }
        }
      )
    }
  }

  private def handleError(
    filledForm: Form[CsvDataSetImportInfo],
    dataSetName: String)(
    message: String
  )(implicit request: Request[_]): Result = {
    logger.error(message)
    createBadRequest(filledForm.withGlobalError(s"Data set '${dataSetName}' upload failed. $message"))
  }

  private def createBadRequest(
    filledForm: Form[CsvDataSetImportInfo]
  )(implicit request: Request[_]) = {
    implicit val msg = messagesApi.preferred(request)
    BadRequest(importView(filledForm))
  }
}