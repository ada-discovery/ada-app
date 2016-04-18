package controllers.dataset

import javax.inject.Inject

import persistence.RepoTypes._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import runnables.DataSetImportInfo
import controllers.dataset.DataSetSettingController.dataSetSettingMapping
import services.DataSetService

class DataSetUploadController @Inject() (
    repo: DataSetSettingRepo,
    dataSetService: DataSetService,
    messagesApi: MessagesApi
  ) extends Controller {

  protected val form = Form(
    mapping(
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText,
      "dataSetName" -> nonEmptyText,
      "path" -> optional(nonEmptyText),
      "delimiter" -> nonEmptyText,
      "eol" -> optional(nonEmptyText),
      "setting" -> optional(dataSetSettingMapping)
    ) (new DataSetImportInfo(_, _, _, _, _, _, _))
      { importInfo: DataSetImportInfo =>
        Some(importInfo.dataSpaceName, importInfo.dataSetId, importInfo.dataSpaceName, importInfo.path, importInfo.delimiter, importInfo.eol, importInfo.setting)
      }
  )

  def create = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(views.html.dataset.uploadDataSet(form))
  }

  def upload = Action { implicit request =>
    form.bindFromRequest.fold(
      { formWithErrors =>
        implicit val msg = messagesApi.preferred(request)
        BadRequest(views.html.dataset.uploadDataSet(formWithErrors))
      },
      importInfo => {
        val importFile = request.body.asMultipartFormData.get.file("importFile")
        val importInfoExt = if (importFile.isDefined) {
          val fileName = importFile.get.filename
          val contentType = importFile.get.contentType
          val file = importFile.get.ref.file
          importInfo.copy(file = Some(file))
        } else
          importInfo

        dataSetService.importDataSet(importInfoExt)
        render {
          case Accepts.Html() => Redirect(controllers.routes.AppController.index).flashing("success" -> s"Data set ${importInfo.dataSetName} has been uploaded")
          case Accepts.Json() => Created(Json.obj("message" -> "Data set has been uploaded", "name" -> importInfo.dataSetName))
        }
      }
    )
  }
}