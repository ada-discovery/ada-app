package controllers.dataset

import java.util.Date

import scala.concurrent.duration._
import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import controllers.ViewTypes.{EditView, CreateView}
import controllers._
import models.DataSetImportFormattersAndIds.{DataSetImportIdentity, dataSetImportFormat}
import models._
import persistence.RepoTypes._
import play.api.Logger
import play.api.data.{Mapping, Form}
import play.api.data.Forms._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import services.{DataSetImportScheduler, DataSetService, DeNoPaSetting}
import util.SecurityUtil.restrictAdmin
import views.html.{datasetimport => importViews}

import scala.concurrent.{Await, Future}

class DataSetImportController @Inject()(
    repo: DataSetImportRepo,
    dataSetService: DataSetService,
    dataSetImportScheduler: DataSetImportScheduler,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi
  ) extends CrudControllerImpl[DataSetImport, BSONObjectID](repo) with AdminRestrictedCrudController[BSONObjectID] {

  private val logger = Logger // (this.getClass)

  // Forms

  protected val scheduledTimeMapping: Mapping[ScheduledTime] = mapping(
    "hour" -> optional(number(min=0, max=23)),
    "minute" -> optional(number(min=0, max=59)),
    "second" -> optional(number(min=0, max=59))
  ) (ScheduledTime.apply)(ScheduledTime.unapply)

  private implicit val seqFormatter = SeqFormatter.apply
  private implicit val mapFormatter = MapJsonFormatter.apply

  private val dataSetSettingMapping: Mapping[DataSetSetting] = mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "dataSetId" -> nonEmptyText,
    "keyFieldName" -> nonEmptyText,
    "exportOrderByFieldName" -> nonEmptyText,
    "listViewTableColumnNames" -> of[Seq[String]],
    "overviewChartFieldNames" -> of[Seq[String]],
    "overviewChartElementGridWidth" -> number(min = 1, max = 12),
    "defaultScatterXFieldName" -> nonEmptyText,
    "defaultScatterYFieldName" -> nonEmptyText,
    "defaultDistributionFieldName" -> nonEmptyText,
    "tranSMARTVisitFieldName" -> optional(text),
    "tranSMARTReplacements" -> default(of[Map[String, String]], Map("\n" -> " ", "\r" -> " "))
  ) (DataSetSetting.apply2){
    DataSetSetting.unapply(_).map( v =>
      (v._1, v._2, v._3, v._4 ,v._5, v._6.map(_.fieldName), v._7, v._8, v._9, v._10, v._11, v._12)
    )
  }

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
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    ) (CsvDataSetImport.apply)(CsvDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val synapseForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "tableId" -> nonEmptyText,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    ) (SynapseDataSetImport.apply)(SynapseDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val tranSmartForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "dataPath" -> optional(text),
      "mappingPath" -> optional(text),
      "charsetName" -> optional(text),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    ) (TranSmartDataSetImport.apply)(TranSmartDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val redCapForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "url" -> nonEmptyText,
      "token" -> nonEmptyText,
      "importDictionaryFlag" -> boolean,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    ) (RedCapDataSetImport.apply)(RedCapDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  private val classNameFormViewsMap = FormWithViews.toMap[DataSetImport](
    Seq(
      FormWithViews[CsvDataSetImport](
        csvForm,
        importViews.createCsvType(_)(_, _, _),
        importViews.editCsvType(_, _)(_, _, _)
      ),

      FormWithViews[SynapseDataSetImport](
        synapseForm,
        importViews.createSynapseType(_)(_, _, _),
        importViews.editSynapseType(_, _)(_, _, _)
      ),

      FormWithViews[TranSmartDataSetImport](
        tranSmartForm,
        importViews.createTranSmartType(_)(_, _, _),
        importViews.editTranSmartType(_, _)(_, _, _)
      ),

      FormWithViews[RedCapDataSetImport](
        redCapForm,
        importViews.createRedCapType(_)(_, _, _),
        importViews.editRedCapType(_, _)(_, _, _)
      )
    ))

  // default form... unused
  override protected val form = csvForm.asInstanceOf[Form[DataSetImport]]

  private val concreteClassFieldName = "concreteClass"

  override protected val home =
    Redirect(routes.DataSetImportController.find())

  override protected def fillForm(entity: DataSetImport): Form[DataSetImport] = {
    val concreteClassName = entity.getClass.getName
    getForm(concreteClassName).fill(entity)
  }

  override protected def formFromRequest(implicit request: Request[AnyContent]): Form[DataSetImport] = {
    val concreteClassName = getParamValue(concreteClassFieldName)
    getForm(concreteClassName).bindFromRequest
  }

  override protected def createView(form: Form[DataSetImport])(implicit msg: Messages, request: Request[_]) = {
    val subCreateView = getViews(form)._1
    subCreateView(form, request.flash, msg, request)
  }

  override protected def editView(id: BSONObjectID, form: Form[DataSetImport])(implicit msg: Messages, request: Request[_]) = {
    val subEditView = getViews(form)._2
    subEditView(id, form, request.flash, msg, request)
  }

  private def getFormWithViews(concreteClassName: String) =
    classNameFormViewsMap.get(concreteClassName).getOrElse(
      throw new AdaException(s"Form and views a sub type '$concreteClassName' not found."))

  private def getForm(concreteClassName: String) =
    getFormWithViews(concreteClassName)._1

  private def getViews(form: Form[DataSetImport]): (CreateView[DataSetImport], EditView[DataSetImport]) = {
    val concreteClassName = form.value.map(_.getClass.getName).getOrElse(form(concreteClassFieldName).value.get)
    val formWithViews = classNameFormViewsMap.get(concreteClassName).getOrElse(
      throw new AdaException(s"Form and views for a sub type '$concreteClassName' not found."))
    (formWithViews._2, formWithViews._3)
  }

  override protected def showView(id: BSONObjectID, form: Form[DataSetImport])(implicit msg: Messages, request: Request[_]) =
    editView(id, form)

  override protected def listView(page: Page[DataSetImport])(implicit msg: Messages, request: Request[_]): Html =
    importViews.list(page, result(dataSpaceMetaInfoRepo.find()))

  def create(concreteClassName: String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)
      val formWithViews = getFormWithViews(concreteClassName)
      Ok(formWithViews._2(formWithViews._1, request.flash, msg, request))
    }
  }

  def execute(id: BSONObjectID) = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set import #$id not found"))
      ) { importInfo =>
          val start = new Date()
          implicit val msg = messagesApi.preferred(request)
          def errorRedirect(errorMessage: String) = home.flashing("errors" -> s"Data set '${importInfo.dataSetName}' import failed. $errorMessage")
          val successRedirect = home// Redirect(new DataSetRouter(importInfo.dataSetId).plainOverviewList)
          dataSetService.importDataSetUntyped(importInfo).map { _ =>
            val execTimeSec = (new Date().getTime - start.getTime) / 1000
            render {
              case Accepts.Html() => successRedirect.flashing("success" -> s"Data set '${importInfo.dataSetName}' has been imported in $execTimeSec sec(s).")
              case Accepts.Json() => Created(Json.obj("message" -> s"Data set has been imported in $execTimeSec sec(s)", "name" -> importInfo.dataSetName))
            }
          }.recover {
            case e: AdaParseException => errorRedirect(s"Parsing problem occurred. ${e.getMessage}")
            case e: AdaException => errorRedirect(e.getMessage)
            case e: Exception => errorRedirect(s"Fatal problem detected. ${e.getMessage}. Contact your admin.")
          }
        }
      )
    }
  }

//  def schedule(id: BSONObjectID) = restrictAdmin(deadbolt) {
//    Action.async { implicit request =>
//      repo.get(id).map(_.fold(
//        NotFound(s"Data set import #$id not found")
//      ) { importInfo =>
//          implicit val msg = messagesApi.preferred(request)
//          dataSetImportScheduler.schedule(importInfo.scheduledTime)(id)
//            render {
//              case Accepts.Html() => home.flashing("success" -> s"Data set '${importInfo.dataSetName}' import has been scheduled.")
//              case Accepts.Json() => Created(Json.obj("message" -> "Data set import has been scheduled", "name" -> importInfo.dataSetName))
//            }
//        }
//      )
//    }
//  }
//
//  def cancel(id: BSONObjectID) = restrictAdmin(deadbolt) {
//    Action.async { implicit request =>
//      repo.get(id).map(_.fold(
//        NotFound(s"Data set import #$id not found")
//      ) { importInfo =>
//        implicit val msg = messagesApi.preferred(request)
//        dataSetImportScheduler.cancel(id)
//        render {
//          case Accepts.Html() => home.flashing("success" -> s"Data set '${importInfo.dataSetName}' import has been scheduled.")
//          case Accepts.Json() => Created(Json.obj("message" -> "Data set import has been scheduled", "name" -> importInfo.dataSetName))
//        }
//      }
//      )
//    }
//  }

  override protected def saveCall(importInfo: DataSetImport)(implicit request: Request[AnyContent]): Future[BSONObjectID] =
    super.saveCall(importInfo).map { id =>
      scheduleOrCancel(id, importInfo); id
    }

  override protected def updateCall(importInfo: DataSetImport)(implicit request: Request[AnyContent]): Future[BSONObjectID] =
    super.updateCall(importInfo).map { id =>
      scheduleOrCancel(id, importInfo); id
    }

  override protected def deleteCall(id: BSONObjectID)(implicit request: Request[AnyContent]): Future[Unit] =
    super.deleteCall(id).map { _ =>
      dataSetImportScheduler.cancel(id); ()
    }

  private def scheduleOrCancel(id: BSONObjectID, importInfo: DataSetImport): Unit = {
    if (importInfo.scheduled)
      dataSetImportScheduler.schedule(importInfo.scheduledTime.get)(id)
    else
      dataSetImportScheduler.cancel(id)
  }

  def uploadCsv = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      val filledForm = csvForm.bindFromRequest
      filledForm.fold(
        { formWithErrors =>
          Future.successful(createBadRequestCsv(formWithErrors))
        },
        importInfo => {
          val dataSetName = importInfo.dataSetName
          val importFileOption = request.body.asMultipartFormData.get.file("importFile")
          val file = importFileOption.map(_.ref.file)

          if (importInfo.path.isEmpty && file.isEmpty)
            Future.successful(createBadRequestCsv(filledForm.withError("path", "No path or import file specified.")))
          else {
            val errorRedirect = handleErrorCsv(filledForm, dataSetName) _
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

  private def handleErrorCsv(
                              filledForm: Form[CsvDataSetImport],
                              dataSetName: String)(
    message: String
  )(implicit request: Request[_]): Result = {
    logger.error(message)
    createBadRequestCsv(filledForm.withGlobalError(s"Data set '${dataSetName}' upload failed. $message"))
  }

  private def createBadRequestCsv(
    filledForm: Form[CsvDataSetImport]
  )(implicit request: Request[_]) = {
    implicit val msg = messagesApi.preferred(request)
    BadRequest(importViews.createCsvType(filledForm))
  }

  def uploadSynapse = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      val filledForm = synapseForm.bindFromRequest
      filledForm.fold(
        { formWithErrors =>
          Future.successful(createBadRequestSynapse(formWithErrors))
        },
        importInfo => {
          val dataSetName = importInfo.dataSetName
          val errorRedirect = handleErrorSynapse(filledForm, dataSetName) _
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
  }

  private def handleErrorSynapse(
                                  filledForm: Form[SynapseDataSetImport],
                                  dataSetName: String)(
    message: String
  )(implicit request: Request[_]): Result = {
    logger.error(message)
    createBadRequestSynapse(filledForm.withGlobalError(s"Data set '${dataSetName}' upload failed. $message"))
  }

  private def createBadRequestSynapse(
    filledForm: Form[SynapseDataSetImport]
  )(implicit request: Request[_]) = {
    implicit val msg = messagesApi.preferred(request)
    BadRequest(importViews.createSynapseType(filledForm))
  }

  def uploadTranSmart = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      val filledForm = tranSmartForm.bindFromRequest
      filledForm.fold(
        { formWithErrors =>
          Future.successful(createBadRequestTranSmart(formWithErrors))
        },
        importInfo => {
          val dataSetName = importInfo.dataSetName
          val dataFile = getFile("dataFile", request)
          val mappingFile = getFile("mappingFile", request)

          if (importInfo.dataPath.isEmpty && dataFile.isEmpty)
            Future.successful(createBadRequestTranSmart(filledForm.withError("path", "No data path or import file specified.")))
          else {
            val errorRedirect = handleErrorTranSmart(filledForm, dataSetName) _
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

  private def handleErrorTranSmart(
                                    filledForm: Form[TranSmartDataSetImport],
                                    dataSetName: String)(
    message: String
  )(implicit request: Request[_]): Result = {
    logger.error(message)
    createBadRequestTranSmart(filledForm.withGlobalError(s"Data set '${dataSetName}' upload failed. $message"))
  }

  private def createBadRequestTranSmart(
    filledForm: Form[TranSmartDataSetImport]
  )(implicit request: Request[_]) = {
    implicit val msg = messagesApi.preferred(request)
    BadRequest(importViews.createTranSmartType(filledForm))
  }
}