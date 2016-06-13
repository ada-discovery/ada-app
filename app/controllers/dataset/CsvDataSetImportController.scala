package controllers.dataset

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import controllers.{AdminRestrictedCrudController, CrudControllerImpl}
import models._
import models.DataSetImportInfoFormattersAndIds.{dataSetImportInfoFormat, DataSetImportInfoIdentity}
import persistence.RepoTypes._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, Action, Controller, Result, Request}
import controllers.dataset.DataSetSettingController.dataSetSettingMapping
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import services.DataSetService
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.SecurityUtil.restrictAdmin
import views.html.dataset.{importinfo => importinfoViews}

import scala.concurrent.Future

class CsvDataSetImportController @Inject()(
    repo: DataSetImportInfoRepo,
    dataSetService: DataSetService,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi
  ) extends CrudControllerImpl[DataSetImportInfo, BSONObjectID](repo) with AdminRestrictedCrudController[BSONObjectID] {

  private val logger = Logger // (this.getClass)

  // TODO
  protected def form = csvForm.asInstanceOf[Form[DataSetImportInfo]]

  protected val csvForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "path" -> optional(text),
      "delimiter" -> nonEmptyText,
      "eol" -> optional(text),
      "charsetName" -> optional(text),
      "setting" -> optional(dataSetSettingMapping)
    ) (CsvDataSetImportInfo.apply)(CsvDataSetImportInfo.unapply)
  )

  override protected val home =
    Redirect(routes.CsvDataSetImportController.listAll())

  override protected def defaultCreateEntity: DataSetImportInfo =
    CsvDataSetImportInfo(None, "", "", "", None, ",", None, None, None)

  override protected def createView(form: Form[DataSetImportInfo])(implicit msg: Messages, request: Request[_]) =
    importinfoViews.createCsvType(form.asInstanceOf[Form[CsvDataSetImportInfo]])

  override protected def showView(id: BSONObjectID, form: Form[DataSetImportInfo])(implicit msg: Messages, request: Request[_]) =
    editView(id, form)

  override protected def editView(id: BSONObjectID, form: Form[DataSetImportInfo])(implicit msg: Messages, request: Request[_]) =
    importinfoViews.editCsvType(id, form.asInstanceOf[Form[CsvDataSetImportInfo]])

  override protected def listView(page: Page[DataSetImportInfo])(implicit msg: Messages, request: Request[_]): Html =
    importinfoViews.list(page)

  def upload = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      val filledForm = csvForm.bindFromRequest
      filledForm.fold(
        { formWithErrors =>
          Future.successful(createBadRequest(formWithErrors))
        },
        importInfo => {
          val dataSetName = importInfo.dataSetName
          val importFileOption = request.body.asMultipartFormData.get.file("importFile")
          val file = importFileOption.map(_.ref.file)

          if (importInfo.path.isEmpty && file.isEmpty)
            Future.successful(createBadRequest(filledForm.withError("path", "No path or import file specified.")))
          else {
            val errorRedirect = handleError(filledForm, dataSetName) _
            val successRedirect = Redirect(new DataSetRouter(importInfo.dataSetId).plainOverviewList)
            dataSetService.importDataSet(importInfo, file).map { _ =>
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
    BadRequest(importinfoViews.createCsvType(filledForm))
  }
}