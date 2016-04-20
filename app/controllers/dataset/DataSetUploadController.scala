package controllers.dataset

import javax.inject.Inject

import models.{AdaException, AdaParseException}
import persistence.RepoTypes._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, Action, Controller}
import runnables.DataSetImportInfo
import controllers.dataset.DataSetSettingController.dataSetSettingMapping
import services.DataSetService
import play.api.Logger
import controllers.dataset.DataSetRouter

class DataSetUploadController @Inject() (
    repo: DataSetSettingRepo,
    dataSetService: DataSetService,
    messagesApi: MessagesApi
  ) extends Controller {

  private val logger = Logger // (this.getClass)

  protected val form = Form(
    mapping(
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should contain no spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "path" -> optional(nonEmptyText),
      "delimiter" -> nonEmptyText,
      "eol" -> optional(nonEmptyText),
      "charsetName" -> optional(nonEmptyText),
      "setting" -> optional(dataSetSettingMapping)
    ) (new DataSetImportInfo(_, _, _, _, _, _, _, _))
      { importInfo: DataSetImportInfo =>
        Some(
          importInfo.dataSpaceName, importInfo.dataSetId,
          importInfo.dataSpaceName, importInfo.path, importInfo.delimiter,
          importInfo.eol, importInfo.charsetName, importInfo.setting
        )
      }
  )

  def create = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(views.html.dataset.uploadDataSet(form))
  }

  def upload = Action { implicit request =>
    val filledForm = form.bindFromRequest
    filledForm.fold(
      { formWithErrors =>
        createBadRequest(formWithErrors)
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
          createBadRequest(filledForm.withError("path", "No path or import file specified."))
        else {
          val errorRedirect = handleError(filledForm, dataSetName) _
          val successRedirect = Redirect(new DataSetRouter(importInfo.dataSetId).plainOverviewList)
          try {
            dataSetService.importDataSet(importInfoExt)
            render {
              case Accepts.Html() => successRedirect.flashing("success" -> s"Data set '$dataSetName' has been uploaded.")
              case Accepts.Json() => Created(Json.obj("message" -> "Data set has been uploaded", "name" -> importInfo.dataSetName))
            }
          } catch {
            case e: AdaParseException => errorRedirect(s"Parsing problem occured: '${e.getMessage}'")
            case e: AdaException => errorRedirect(e.getMessage)
            case e: Exception => errorRedirect(s"Fatal problem detected: '${e.getMessage}'. Contact your admin.")
          }
        }
      }
    )
  }

  private def handleError(
    filledForm: Form[DataSetImportInfo],
    dataSetName: String)(
    message: String
  )(implicit request: RequestHeader) = {
    logger.error(message)
    createBadRequest(filledForm.withGlobalError(s"Data set '${dataSetName}' upload failed. $message"))
  }

  private def createBadRequest(
    filledForm: Form[DataSetImportInfo]
  )(implicit request: RequestHeader) = {
    implicit val msg = messagesApi.preferred(request)
    BadRequest(views.html.dataset.uploadDataSet(filledForm))
  }
}