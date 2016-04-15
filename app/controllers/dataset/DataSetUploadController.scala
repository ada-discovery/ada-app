package controllers.dataset

import javax.inject.Inject

import persistence.RepoTypes._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import runnables.{DataSetImportInfo, ImportDataSetFactory}
import controllers.dataset.DataSetSettingController.dataSetSettingMapping

class DataSetUploadController @Inject() (
    repo: DataSetSettingRepo,
    importDataSetFactory: ImportDataSetFactory,
    messagesApi: MessagesApi
  ) extends Controller {

  protected val form = Form(
    mapping(
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText,
      "dataSetName" -> nonEmptyText,
      "path" -> nonEmptyText,
      "delimiter" -> nonEmptyText,
      "eol" -> optional(nonEmptyText),
      "setting" -> optional(dataSetSettingMapping)
    ) (DataSetImportInfo.apply)(DataSetImportInfo.unapply))

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
        println(importInfo.setting.get.dataSetId)
        if (importInfo.setting.isDefined) {
          // if the setting is defined use a given data set id
          importInfo.copy(setting = Some(importInfo.setting.get.copy(dataSetId = importInfo.dataSetId)))
        }
        importDataSetFactory(importInfo).run()
        render {
          case Accepts.Html() => Redirect(controllers.routes.AppController.index).flashing("success" -> s"Data set ${importInfo.dataSetName} has been uploaded")
          case Accepts.Json() => Created(Json.obj("message" -> "Data set has been uploaded", "name" -> importInfo.dataSetName))
        }
      }
    )
  }
}