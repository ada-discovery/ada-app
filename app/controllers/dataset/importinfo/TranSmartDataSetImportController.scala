package controllers.dataset.importinfo

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import controllers.dataset.DataSetRouter
import controllers.dataset.DataSetSettingController.dataSetSettingMapping
import models.{AdaException, AdaParseException, TranSmartDataSetImportInfo}
import persistence.RepoTypes._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import services.{DataSetService, DeNoPaSetting}
import util.SecurityUtil.restrictAdmin
import views.html.dataset.importinfo._

import scala.concurrent.Future

class TranSmartDataSetImportController @Inject()(
    repo: DataSetSettingRepo,
    dataSetService: DataSetService,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi
  ) extends Controller {

  private val logger = Logger // (this.getClass)

  protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "dataPath" -> optional(text),
      "mappingPath" -> optional(text),
      "charsetName" -> optional(text),
      "setting" -> optional(dataSetSettingMapping)
    ) (TranSmartDataSetImportInfo.apply)(TranSmartDataSetImportInfo.unapply)
  )

  private val defaultImportInfo =
    TranSmartDataSetImportInfo(None, "", "", "", None, None, None, None)

  def create = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)
      Ok(createTranSmartType(form.fill(defaultImportInfo)))
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
          val dataFile = getFile("dataFile", request)
          val mappingFile = getFile("mappingFile", request)

          if (importInfo.dataPath.isEmpty && dataFile.isEmpty)
            Future.successful(createBadRequest(filledForm.withError("path", "No data path or import file specified.")))
          else {
            val errorRedirect = handleError(filledForm, dataSetName) _
            val successRedirect = Redirect(new DataSetRouter(importInfo.dataSetId).plainOverviewList)
            dataSetService.importDataSetAndDictionary(importInfo, dataFile, mappingFile, DeNoPaSetting.typeInferenceProvider).map { _ =>
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

  private def getFile(fileParamKey: String, request: Request[AnyContent]): Option[java.io.File] = {
    val dataFileOption = request.body.asMultipartFormData.get.file(fileParamKey)
    dataFileOption.map { dataFile =>
//      val fileName = dataFile.filename
//      val contentType = dataFile.contentType
      dataFile.ref.file
    }
  }

  private def handleError(
    filledForm: Form[TranSmartDataSetImportInfo],
    dataSetName: String)(
    message: String
  )(implicit request: Request[_]): Result = {
    logger.error(message)
    createBadRequest(filledForm.withGlobalError(s"Data set '${dataSetName}' upload failed. $message"))
  }

  private def createBadRequest(
    filledForm: Form[TranSmartDataSetImportInfo]
  )(implicit request: Request[_]) = {
    implicit val msg = messagesApi.preferred(request)
    BadRequest(createTranSmartType(filledForm))
  }
}