package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import controllers.{ExportableAction, CrudControllerImpl, EnumFormatter, MapJsonFormatter}
import dataaccess.RepoTypes.DictionaryCategoryRepo
import dataaccess._
import models._
import dataaccess.DataSetFormattersAndIds._
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import DictionaryFieldRepo._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Action, RequestHeader, Request}
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import services.{DeNoPaSetting, DataSetService}
import util.FilterCondition
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import views.html.dictionary
import scala.concurrent.duration._

import scala.concurrent.Future
import scala.concurrent.Await.result

trait DictionaryControllerFactory {
  def apply(dataSetId: String): DictionaryController
}

protected[controllers] class DictionaryControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends CrudControllerImpl[Field, String](dsaf(dataSetId).get.fieldRepo) with DictionaryController with ExportableAction[Field] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get
  protected val categoryRepo: DictionaryCategoryRepo = dsa.categoryRepo
  protected lazy val dataSetName = result(dsa.metaInfo).name

  protected override val listViewColumns = Some(Seq("name", "fieldType", "isArray", "label", "categoryId"))

  private val exportOrderByFieldName = "name"
  private val csvFileName = "dictionary_" + dataSetId.replace(" ", "-") + ".csv"
  private val jsonFileName = "dictionary_" + dataSetId.replace(" ", "-") + ".json"
  private val csvExportFieldNames = Seq("name", "fieldType", "numValues", "isArray", "label", "categoryId")

  implicit val fieldTypeFormatter = EnumFormatter(FieldType)
  implicit val mapFormatter = MapJsonFormatter.apply

  override protected val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "fieldType" -> of[FieldType.Value],
      "isArray" -> boolean,
      "numValues" -> optional(of[Map[String, String]]),
      "aliases" ->  seq(nonEmptyText),
      "label" ->  optional(nonEmptyText),
      "categoryId" -> optional(nonEmptyText)
      // TODO: make it more pretty perhaps by moving the category stuff to proxy/subclass of Field
    ) { (name, fieldType, isArray, numValues, aliases, label, categoryId) =>
      Field(name, fieldType, isArray, numValues, aliases, label, categoryId.map(BSONObjectID(_)))
    }
    ((field: Field) => Some(field.name, field.fieldType, field.isArray, field.numValues, field.aliases, field.label, field.categoryId.map(_.stringify)))
  )

  // router for requests; to be passed to views as helper.
  protected val router: DictionaryRouter = new DictionaryRouter(dataSetId)
  protected val jsRouter: DictionaryJsRouter = new DictionaryJsRouter(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  override protected def createView(f : Form[Field])(implicit msg: Messages, request: Request[_]) =
    dictionary.create(dataSetName + " Field", f, allCategories, router)

  override protected def showView(name: String, f : Form[Field])(implicit msg: Messages, request: Request[_]) =
    editView(name, f)

  override protected def editView(name: String, f : Form[Field])(implicit msg: Messages, request: Request[_]) =
    dictionary.edit(dataSetName + " Field", name, f, allCategories, router, result(dataSpaceMetaInfoRepo.find()))

  // TODO: Remove
  override protected def listView(page: Page[Field])(implicit msg: Messages, request: Request[_]) =
    throw new IllegalStateException("List not implemented... use overviewList instead.")

  // TODO: change to an async call
  protected def allCategories = {
    val categoriesFuture = categoryRepo.find(sort = Seq(AscSort("name")))
    result(categoriesFuture)
  }

//  override protected  def getCall(name: String) =
//    for {
//      field <- repo.get(name)
//      _ <- setCategoryById(categoryRepo, field.get) if (field.isDefined)
//    } yield field

//    repo.get(name).flatMap(_.fold(
//      Future(Option.empty[Field])
//    ) { field =>
//      setCategoryById(categoryRepo, field).map(_ => Some(field))
//    })

  private def overviewListView(
    page: Page[Field],
    fieldChartSpecs : Iterable[FieldChartSpec],
    dataSpaceMetaInfos: Traversable[DataSpaceMetaInfo]
  )(implicit msg: Messages, request: Request[_]) =
    dictionary.list(
      dataSetName + " Field",
      page,
      fieldChartSpecs,
      router,
      dataSpaceMetaInfos,
      6
    )

  override def overviewList(page: Int, orderBy: String, filter: Seq[FilterCondition]) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val fieldNameExtractors = Seq(
      ("Field Type", "fieldType", (field : Field) => field.fieldType)
//      ("Enum", "isEnum", (field : Field) => field.isEnum)
    )
    val futureFieldChartSpecs = fieldNameExtractors.map { case (title, fieldName, fieldExtractor) =>
      getDictionaryChartSpec(title, filter, fieldName, fieldExtractor).map(chartSpec => FieldChartSpec(fieldName, chartSpec))
    }
    val fieldChartSpecsFuture = Future.sequence(futureFieldChartSpecs)
    val futureMetaInfos = dataSpaceMetaInfoRepo.find()
    val (futureItems, futureCount) = getFutureItemsAndCount(page, orderBy, filter)

    {
      for {
        items <- futureItems
        count <- futureCount
        metaInfos <- futureMetaInfos
        fieldChartSpecs <- fieldChartSpecsFuture
        _ <- setCategoriesById(categoryRepo, items)
      } yield
        Ok(overviewListView(Page(items, page, page * pageLimit, count, orderBy, filter), fieldChartSpecs, metaInfos))
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the dictionary list process")
        InternalServerError(t.getMessage)
    }
  }

  def inferDictionary = Action.async { implicit request =>
    // TODO: introduce type inference setting for each data set
    dataSetService.inferDictionary(dataSetId, DeNoPaSetting.typeInferenceProvider).map( _ =>
      home.flashing("success" -> s"Dictionary for '${dataSetId}'  was successfully inferred.")
    )
  }

  /**
    * Generate content of csv export file and create download.
    *
    * @param delimiter Delimiter for csv output file.
    * @return View for download.
    */
  override def exportAllRecordsAsCsv(delimiter : String) =
    exportAllToCsv(csvFileName, delimiter, exportOrderByFieldName, Some(csvExportFieldNames))

  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  override def exportAllRecordsAsJson =
    exportAllToJson(jsonFileName, exportOrderByFieldName)

  /**
    * Generate content of csv export file and create download.
    *
    * @param delimiter Delimiter for csv output file.
    * @return View for download.
    */
  override def exportRecordsAsCsv(delimiter : String, filter: Seq[FilterCondition]) =
    exportToCsv(csvFileName, delimiter, filter, exportOrderByFieldName, Some(csvExportFieldNames))

  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  override def exportRecordsAsJson(filter: Seq[FilterCondition]) =
    exportToJson(jsonFileName, filter, exportOrderByFieldName)

  override def updateLabel(id: String, label: String) = Action.async { implicit request =>
    repo.get(id).flatMap(_.fold(
      Future(NotFound(s"Field '$id' not found"))
    ){ field =>
      updateCall(field.copy(label = Some(label))).map(_ => Ok("Done"))
    })
  }

  override def jsRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("dictionaryJsRoutes")(
        jsRouter.updateLabel
      )
    ).as("text/javascript")
  }

  private def getDictionaryChartSpec(
    chartTitle : String,
    filter: Seq[FilterCondition],
    fieldName : String,
    fieldExtractor : Field => Any
  ) : Future[ChartSpec] =
    repo.find(
      criteria = toCriteria(filter),
      projection = Seq(fieldName)
    ).map { fields =>
      val values = fields.map(field => Some(fieldExtractor(field).toString))
      ChartSpec.categorical(values, None, chartTitle, false, true)
    }
}