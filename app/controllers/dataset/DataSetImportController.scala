package controllers.dataset

import java.util.Date
import java.util.concurrent.TimeoutException

import models.DataSetSetting
import services.datasetimporter.DataSetImporterCentral

import scala.concurrent.duration._
import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import controllers._
import models.DataSetImportFormattersAndIds.{DataSetImportIdentity, dataSetImportFormat}
import models._
import persistence.RepoTypes.{DataSetImportRepo, MessageRepo}
import play.api.{Configuration, Logger}
import play.api.data.{Form, Mapping}
import play.api.data.Forms.{optional, _}
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import java.io.{File, FileInputStream, FileOutputStream}

import controllers.core._
import dataaccess.AscSort
import services.{DataSetImportScheduler, DataSetService, DataSpaceService}
import util.SecurityUtil.restrictAdminAnyNoCaching
import views.html.{datasetimport => view}
import views.html.layout
import util.{MessageLogger, getRequestParamValue}
import play.api.data.format.Formats._
import _root_.util.retry
import models.DataSetFormattersAndIds.JsObjectIdentity

import scala.concurrent.{Await, Future}

class DataSetImportController @Inject()(
    repo: DataSetImportRepo,
    dataSetService: DataSetService,
    dataSetImporterCentral: DataSetImporterCentral,
    dataSetImportScheduler: DataSetImportScheduler,
    dataSpaceService: DataSpaceService,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    messageRepo: MessageRepo,
    configuration: Configuration
  ) extends CrudControllerImpl[DataSetImport, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[DataSetImport, BSONObjectID]
    with HasFormShowEqualEditView[DataSetImport, BSONObjectID] {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  // (this.getClass)
  private val importFolder = configuration.getString("datasetimport.import.folder").get
  private val importRetryNum = configuration.getInt("datasetimport.retrynum").getOrElse(3)

  // Forms

  protected val scheduledTimeMapping: Mapping[ScheduledTime] = mapping(
    "hour" -> optional(number(min = 0, max = 23)),
    "minute" -> optional(number(min = 0, max = 59)),
    "second" -> optional(number(min = 0, max = 59))
  )(ScheduledTime.apply)(ScheduledTime.unapply)

  private implicit val seqFormatter = SeqFormatter.apply
  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)
  private implicit val storageTypeFormatter = EnumFormatter(StorageType)
  private implicit val widgetGenerationMethodFormatter = EnumFormatter(WidgetGenerationMethod)

  private val dataSetSettingMapping: Mapping[DataSetSetting] = mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "dataSetId" -> nonEmptyText,
    "keyFieldName" -> default(nonEmptyText, JsObjectIdentity.name),
    "exportOrderByFieldName" -> optional(text),
    "defaultScatterXFieldName" -> optional(text),
    "defaultScatterYFieldName" -> optional(text),
    "defaultDistributionFieldName" -> optional(text),
    "defaultCumulativeCountFieldName" -> optional(text),
    "filterShowFieldStyle" -> optional(of[FilterShowFieldStyle.Value]),
    "filterShowNonNullCount" -> boolean,
    "tranSMARTVisitFieldName" -> optional(text),
    "tranSMARTReplacements" -> default(of[Map[String, String]], Map("\n" -> " ", "\r" -> " ")),
    "storageType" -> of[StorageType.Value],
    "mongoAutoCreateIndexForProjection" -> boolean,
    "cacheDataSet" -> boolean
  )(DataSetSetting.apply)(DataSetSetting.unapply)

  private val dataViewMapping: Mapping[DataView] = mapping(
    "tableColumnNames" -> of[Seq[String]],
    "distributionCalcFieldNames" -> of[Seq[String]],
    "elementGridWidth" -> number(min = 1, max = 12),
    "generationMethod" -> of[WidgetGenerationMethod.Value]
  )(DataView.applyMain) { (item: DataView) =>
    Some((
      item.tableColumnNames,
      item.widgetSpecs.collect { case p: DistributionWidgetSpec => p }.map(_.fieldName),
      item.elementGridWidth,
      item.generationMethod
    ))
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
      "matchQuotes" -> boolean,
      "inferFieldTypes" -> boolean,
      "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
      "inferenceMinAvgValuesPerEnum" -> optional(of[Double]),
      "arrayDelimiter" -> optional(text),
      "booleanIncludeNumbers" -> boolean,
      "saveBatchSize" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(CsvDataSetImport.apply)(CsvDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val jsonForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "path" -> optional(text),
      "charsetName" -> optional(text),
      "inferFieldTypes" -> boolean,
      "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
      "inferenceMinAvgValuesPerEnum" -> optional(of[Double]),
      "booleanIncludeNumbers" -> boolean,
      "saveBatchSize" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(JsonDataSetImport.apply)(JsonDataSetImport.unapply)
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
      "batchSize" -> optional(number(min = 1)),
      "bulkDownloadGroupNumber" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(SynapseDataSetImport.apply)(SynapseDataSetImport.unapply)
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
      "matchQuotes" -> boolean,
      "inferFieldTypes" -> boolean,
      "inferenceMaxEnumValuesCount" -> optional(number(min = 1)),
      "inferenceMinAvgValuesPerEnum" -> optional(of[Double]),
      "saveBatchSize" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(TranSmartDataSetImport.apply)(TranSmartDataSetImport.unapply)
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
      "eventNames" -> of[Seq[String]],
      "categoriesToInheritFromFirstVisit" -> of[Seq[String]],
      "saveBatchSize" -> optional(number(min = 1)),
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(RedCapDataSetImport.apply)(RedCapDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected val eGaitForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSpaceName" -> nonEmptyText,
      "dataSetId" -> nonEmptyText.verifying("Data Set Id should not contain any spaces", dataSetId => !dataSetId.contains(" ")),
      "dataSetName" -> nonEmptyText,
      "importRawData" -> boolean,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "setting" -> optional(dataSetSettingMapping),
      "dataView" -> optional(dataViewMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(EGaitDataSetImport.apply)(EGaitDataSetImport.unapply)
      .verifying(
        "Import is marked as 'scheduled' but no time provided",
        importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
      )
  )

  protected case class DataSetImportCreateEditViews[E <: DataSetImport](
    name: String,
    val form: Form[E],
    viewElements: (Form[E], Messages) => Html)(
    implicit manifest: Manifest[E]
  ) extends CreateEditFormViews[E, BSONObjectID] {

    override protected[controllers] def fillForm(item: E) =
      form.fill(item)

    override protected[controllers] def createView = { implicit ctx: WebContext =>
      form: Form[E] =>
        layout.create(
          name,
          form,
          viewElements(form, ctx.msg),
          controllers.dataset.routes.DataSetImportController.save,
          controllers.dataset.routes.DataSetImportController.listAll(),
          'enctype -> "multipart/form-data"
        )
    }

    override protected[controllers] def editView = { implicit ctx: WebContext =>
      data: IdForm[BSONObjectID, E] =>
        layout.edit(
          name,
          data.form.errors,
          viewElements(data.form, ctx.msg),
          controllers.dataset.routes.DataSetImportController.update(data.id),
          controllers.dataset.routes.DataSetImportController.listAll(),
          Some(controllers.dataset.routes.DataSetImportController.delete(data.id)),
          None,
          None,
          None,
          Seq('enctype -> "multipart/form-data")
        )
    }
  }

  override protected val createEditFormViews =
    Seq(
      DataSetImportCreateEditViews[CsvDataSetImport](
        "CSV Data Set Import",
        csvForm,
        view.csvTypeElements(_)(_)
      ),

      DataSetImportCreateEditViews[JsonDataSetImport](
        "JSON Data Set Import",
        jsonForm,
        view.jsonTypeElements(_)(_)
      ),

      DataSetImportCreateEditViews[SynapseDataSetImport](
        "Synapse Data Set Import",
        synapseForm,
        view.synapseTypeElements(_)(_)
      ),

      DataSetImportCreateEditViews[TranSmartDataSetImport](
        "TranSMART Data Set (and Dictionary) Import",
        tranSmartForm,
        view.tranSmartTypeElements(_)(_)
      ),

      DataSetImportCreateEditViews[RedCapDataSetImport](
        "RedCap Data Set Import",
        redCapForm,
        view.redCapTypeElements(_)(_)
      ),

      DataSetImportCreateEditViews[EGaitDataSetImport](
        "eGait Data Set Import",
        eGaitForm,
        view.eGaitTypeElements(_)(_)
      )
    )

  // default form... unused
  override protected[controllers] val form = csvForm.asInstanceOf[Form[DataSetImport]]

  override protected val home =
    Redirect(routes.DataSetImportController.find())

  override protected type ListViewData = (Page[DataSetImport], Traversable[DataSpaceMetaInfo])

  override protected def getListViewData(
    page: Page[DataSetImport]
  ) = { request =>
    for {
      tree <- dataSpaceService.getTreeForCurrentUser(request)
    } yield
      (page, tree)
  }

  override protected[controllers] def listView = { implicit ctx => (view.list(_, _)).tupled}

  def create(concreteClassName: String) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>

      def createAux[E <: DataSetImport](x: CreateEditFormViews[E, BSONObjectID]): Future[Result] =
        x.getCreateViewData.map { viewData =>
          Ok(x.createView(implicitly[WebContext])(viewData))
        }

      createAux(getFormWithViews(concreteClassName))
  }

  def execute(id: BSONObjectID) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Data set import #$id not found"))
      ) { importInfo =>
          val start = new Date()
          implicit val msg = messagesApi.preferred(request)
          def errorRedirect(errorMessage: String, error: Option[Exception] = None) = {
            val fullMessage = s"Data set '${importInfo.dataSetName}' import failed. $errorMessage"
            if (error.isDefined)
              logger.error(fullMessage, error.get)
            else
              logger.error(fullMessage)
            home.flashing("errors" -> fullMessage)
          }
          val successRedirect = home// Redirect(new DataSetRouter(importInfo.dataSetId).plainOverviewList)
          retry(s"Data set '${importInfo.dataSetName}' import failed: ", logger, importRetryNum)(
            dataSetImporterCentral(importInfo)
          ).map { _ =>
            val execTimeSec = (new Date().getTime - start.getTime) / 1000
//            messageLogger.info()
            render {
              case Accepts.Html() => successRedirect.flashing("success" -> s"Data set '${importInfo.dataSetName}' has been imported in $execTimeSec sec(s).")
              case Accepts.Json() => Created(Json.obj("message" -> s"Data set has been imported in $execTimeSec sec(s)", "name" -> importInfo.dataSetName))
            }
          }.recover {
            case e: AdaParseException => errorRedirect(s"Parsing problem occurred. ${e.getMessage}")
            case e: AdaException => errorRedirect(e.getMessage, Some(e))
            case e: Exception => errorRedirect(s"Fatal problem detected. ${e.getMessage}. Contact your admin.", Some(e))
          }
        }
      )
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

  def idAndNames = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      for {
        imports <- repo.find(sort = Seq(AscSort("name")))
      } yield {
        val idAndNames = imports.map(dataView =>
          Json.obj(
            "_id" -> dataView._id,
            "name" -> dataView.dataSetId
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
  }

  def copy(id: BSONObjectID) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Entity #$id not found"))
      ) { dataSetImport =>
        // TODO: handle the timestamp and data set id/name
        val newDataSetImport = DataSetImportIdentity.clear(dataSetImport)
        super.saveCall(newDataSetImport).map { newId =>
          scheduleOrCancel(newId, newDataSetImport)
          Redirect(routes.DataSetImportController.get(newId)).flashing("success" -> s"Data Set import '${dataSetImport.dataSetId}' has been copied.")
        }
      }
    )
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
            getRequestParamValue("path"))
        importInfo.copy(path = Some(path))
      }

      case importInfo: JsonDataSetImport => {
        val path =
          getFile("dataFile", request).map(dataFile =>
            copyImportFile(dataFile._1, dataFile._2)
          ).getOrElse(
            getRequestParamValue("path"))
        importInfo.copy(path = Some(path))
      }

      case importInfo: TranSmartDataSetImport => {
        val dataPath =
          getFile("dataFile", request).map(dataFile =>
            copyImportFile(dataFile._1, dataFile._2)
          ).getOrElse(
            getRequestParamValue("dataPath"))

        val mappingPath = getFile("mappingFile", request).map(mappingFile =>
          copyImportFile(mappingFile._1, mappingFile._2)
        ).getOrElse(
          getRequestParamValue("mappingPath"))

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
    val dataFileOption = request.body.asMultipartFormData.flatMap(_.file(fileParamKey))
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