package controllers.dataset

import java.util.Date
import java.util.concurrent.TimeoutException

import dataaccess.RepoException
import models.DataSetSetting
import persistence.dataset.DataSpaceMetaInfoRepo
import services.datasetimporter.DataSetImporterCentral

import scala.concurrent.duration._
import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import controllers.ViewTypes.{EditView, CreateView}
import controllers._
import models.DataSetImportFormattersAndIds.{DataSetImportIdentity, dataSetImportFormat}
import models._
import persistence.RepoTypes._
import play.api.{Configuration, Logger}
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import java.io.{File, FileInputStream, FileOutputStream}
import services.{DataSetImportScheduler, DataSetService, DeNoPaSetting}
import util.SecurityUtil.restrictAdmin
import views.html.{datasetimport => importViews}
import views.html.layout

import scala.concurrent.{Await, Future}

class DataSetImportController @Inject()(
    repo: DataSetImportRepo,
    dataSetService: DataSetService,
    dataSetImporterCentral: DataSetImporterCentral,
    dataSetImportScheduler: DataSetImportScheduler,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    configuration: Configuration
  ) extends CrudControllerImpl[DataSetImport, BSONObjectID](repo) with AdminRestrictedCrudController[BSONObjectID] {

  private val logger = Logger // (this.getClass)
  private val importFolder = configuration.getString("datasetimport.import.folder").get

  // Forms

  protected val scheduledTimeMapping: Mapping[ScheduledTime] = mapping(
    "hour" -> optional(number(min=0, max=23)),
    "minute" -> optional(number(min=0, max=59)),
    "second" -> optional(number(min=0, max=59))
  ) (ScheduledTime.apply)(ScheduledTime.unapply)

  private implicit val seqFormatter = SeqFormatter.apply
  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)

  private val dataSetSettingMapping: Mapping[DataSetSetting] = mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "dataSetId" -> nonEmptyText,
    "keyFieldName" -> nonEmptyText,
    "exportOrderByFieldName" -> optional(text),
    "defaultScatterXFieldName" -> nonEmptyText,
    "defaultScatterYFieldName" -> nonEmptyText,
    "defaultDistributionFieldName" -> nonEmptyText,
    "defaultDateCountFieldName" -> nonEmptyText,
    "filterShowFieldStyle" -> optional(of[FilterShowFieldStyle.Value]),
    "tranSMARTVisitFieldName" -> optional(text),
    "tranSMARTReplacements" -> default(of[Map[String, String]], Map("\n" -> " ", "\r" -> " ")),
    "cacheDataSet" -> boolean
  ) (DataSetSetting.apply) (DataSetSetting.unapply)

  private val dataViewMapping: Mapping[DataView] = mapping(
    "tableColumnNames" -> of[Seq[String]],
    "distributionCalcFieldNames" -> of[Seq[String]],
    "elementGridWidth" -> number(min = 1, max = 12)
  ) (DataView.applyMain)
  {(item: DataView) => Some((
    item.tableColumnNames,
    item.statsCalcSpecs.collect { case p: DistributionCalcSpec => p}.map(_.fieldName),
    item.elementGridWidth
  ))}

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
      "matchQuotes" -> boolean,
      "createDummyDictionary" -> boolean,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
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
      "downloadColumnFiles" -> boolean,
      "downloadRecordBatchSize" -> optional(number(min=1)),
      "bulkDownloadGroupNumber" -> optional(number(min=1)),
      "createDummyDictionary" -> boolean,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
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
      "createDummyDictionary" -> boolean,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
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
      "createDummyDictionary" -> boolean,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
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
        createView(
          "CSV Data Set Import",
          importViews.csvTypeElements(_)(_)
        ),
        editView(
          "CSV Data Set Import",
          importViews.csvTypeElements(_)(_)
        )
      ),

      FormWithViews[SynapseDataSetImport](
        synapseForm,
        createView(
          "Synapse Data Set Import",
          importViews.synapseTypeElements(_)(_)
        ),
        editView(
          "Synapse Data Set Import",
          importViews.synapseTypeElements(_)(_)
        )
      ),

      FormWithViews[TranSmartDataSetImport](
        tranSmartForm,
        createView(
          "TranSMART Data Set (and Dictionary) Import",
          importViews.tranSmartTypeElements(_)(_)
        ),
        editView(
          "TranSMART Data Set (and Dictionary) Import",
          importViews.tranSmartTypeElements(_)(_)
        )
      ),

      FormWithViews[RedCapDataSetImport](
        redCapForm,
        createView(
          "RedCap Data Set Import",
          importViews.redCapTypeElements(_)(_)
        ),
        editView(
          "RedCap Data Set Import",
          importViews.redCapTypeElements(_)(_)
        )
      )
    ))

  private def createView[T <: DataSetImport](
    name: String,
    elements: (Form[T], Messages) => Html)(
    f: Form[T],
    flash: Flash,
    msg: Messages,
    request: Request[_]
  ) =
    layout.create(
      name,
      f,
      elements(f, msg),
      controllers.dataset.routes.DataSetImportController.save,
      controllers.routes.AppController.index,
      'enctype -> "multipart/form-data"
    )(flash, msg, request)

  private def editView[T <: DataSetImport](
    name: String,
    elements: (Form[T], Messages) => Html)(
    id: BSONObjectID,
    f: Form[T],
    flash: Flash,
    msg: Messages,
    request: Request[_]
  ) =
    layout.edit(
      name,
      f.errors,
      elements(f, msg),
      controllers.dataset.routes.DataSetImportController.update(id),
      controllers.dataset.routes.DataSetImportController.listAll(),
      Some(controllers.dataset.routes.DataSetImportController.delete(id)),
      None,
      None,
      'enctype -> "multipart/form-data"
    )(flash, msg, request)

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
    importViews.list(page, result(DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)))

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
          def errorRedirect(errorMessage: String) = {
            val fullMessage = s"Data set '${importInfo.dataSetName}' import failed. $errorMessage"
            logger.error(fullMessage)
            home.flashing("errors" -> fullMessage)
          }
          val successRedirect = home// Redirect(new DataSetRouter(importInfo.dataSetId).plainOverviewList)
          dataSetImporterCentral(importInfo).map { _ =>
            val execTimeSec = (new Date().getTime - start.getTime) / 1000
            render {
              case Accepts.Html() => successRedirect.flashing("success" -> s"Data set '${importInfo.dataSetName}' has been imported in $execTimeSec sec(s).")
              case Accepts.Json() => Created(Json.obj("message" -> s"Data set has been imported in $execTimeSec sec(s)", "name" -> importInfo.dataSetName))
            }
          }.recover {
            case e: AdaParseException => errorRedirect(s"Parsing problem occurred. ${e.getMessage}")
            case e: AdaException => errorRedirect(e.getMessage)
            case e: Exception => {
              errorRedirect(s"Fatal problem detected. ${e.getMessage}. Contact your admin.")
            }
          }
        }
      )
    }
  }

  override protected def saveCall(importInfo: DataSetImport)(implicit request: Request[AnyContent]): Future[BSONObjectID] = {
    val id = BSONObjectID.generate
    val modifiedImportInfo = handleImportFiles(DataSetImportIdentity.set(importInfo, id))

    super.saveCall(modifiedImportInfo).map { id =>
      scheduleOrCancel(id, importInfo); id
    }
  }

  override protected def updateCall(importInfo: DataSetImport)(implicit request: Request[AnyContent]): Future[BSONObjectID] = {
    val modifiedImportInfo = handleImportFiles(importInfo)

    //TODO: remove the old files if any
    super.updateCall(modifiedImportInfo).map { id =>
      scheduleOrCancel(id, modifiedImportInfo); id
    }
  }

  private def handleImportFiles(importInfo: DataSetImport)(implicit request: Request[AnyContent]): DataSetImport = {
    val id = importInfo._id.get

    def copyImportFile(name: String, file: File): String = {
      val path = importFolder + id.stringify + "/" + name
      copyFile(file, path)
      path
    }

    importInfo match {
      case importInfo: CsvDataSetImport => {
        val path =
          getFile("dataFile", request).map(dataFile =>
            copyImportFile(dataFile._1, dataFile._2)
          ).getOrElse(
            getParamValue("path"))
        importInfo.copy(path = Some(path))
      }
      case importInfo: TranSmartDataSetImport => {
        val dataPath =
          getFile("dataFile", request).map(dataFile =>
            copyImportFile(dataFile._1, dataFile._2)
          ).getOrElse(
            getParamValue("dataPath"))

        val mappingPath = getFile("mappingFile", request).map(mappingFile =>
          copyImportFile(mappingFile._1, mappingFile._2)
        ).getOrElse(
          getParamValue("mappingPath"))

        importInfo.copy(dataPath = Some(dataPath), mappingPath = Some(mappingPath))
      }
      case _ => importInfo
    }
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

  private def getFile(fileParamKey: String, request: Request[AnyContent]): Option[(String, java.io.File)] = {
    val dataFileOption = request.body.asMultipartFormData.get.file(fileParamKey)
    dataFileOption.map { dataFile =>
      (dataFile.filename, dataFile.ref.file)
    }
  }

  private def copyFile(src: File, location: String): Unit = {
    val dest = new File(location)
    val destFolder = dest.getCanonicalFile.getParentFile
    if (!destFolder.exists()) {
      destFolder.mkdirs()
    }
    new FileOutputStream(dest) getChannel() transferFrom(
      new FileInputStream(src) getChannel, 0, Long.MaxValue )
  }
}