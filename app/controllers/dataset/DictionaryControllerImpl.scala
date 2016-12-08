package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import controllers.{ExportableAction, CrudControllerImpl, EnumFormatter, MapJsonFormatter}
import dataaccess.RepoTypes.CategoryRepo
import dataaccess._
import models._
import models.DataSetFormattersAndIds._
import persistence.RepoTypes._
import persistence.dataset.{DataSpaceMetaInfoRepo, DataSetAccessor, DataSetAccessorFactory}
import dataaccess.FieldRepo._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Action, RequestHeader, Request}
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import services.{DeNoPaSetting, DataSetService}
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
  protected val categoryRepo: CategoryRepo = dsa.categoryRepo
  protected lazy val dataSetName = result(dsa.metaInfo).name

  protected override val listViewColumns = Some(Seq("name", "fieldType", "isArray", "label", "categoryId"))

  private val exportOrderByFieldName = "name"
  private val csvFileName = "dictionary_" + dataSetId.replace(" ", "-") + ".csv"
  private val jsonFileName = "dictionary_" + dataSetId.replace(" ", "-") + ".json"

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  implicit val fieldTypeFormatter = EnumFormatter(FieldTypeId)
  implicit val mapFormatter = MapJsonFormatter.apply

  private val fieldNameLabels = Seq(
    ("fieldType", Some("Field Type")),
    ("isArray", Some("Is Array?")),
    ("label", Some("Label")),
    ("name", Some("Name"))
  )
  private val fieldNameLabelMap = fieldNameLabels.toMap

  override protected val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "label" ->  optional(nonEmptyText),
      "fieldType" -> of[FieldTypeId.Value],
      "isArray" -> boolean,
      "numValues" -> optional(of[Map[String, String]]),
      "displayDecimalPlaces" ->  optional(number(0, 20)),
      "displayTrueValue" ->  optional(nonEmptyText),
      "displayFalseValue" ->  optional(nonEmptyText),
      "aliases" ->  seq(nonEmptyText),
      "categoryId" -> optional(nonEmptyText)
      // TODO: make it more pretty perhaps by moving the category stuff to proxy/subclass of Field
    ) { (name, label, fieldType, isArray, numValues, displayDecimalPlaces, displayTrueValue, displayFalseValue, aliases, categoryId) =>
      Field(name, label, fieldType, isArray, numValues, displayDecimalPlaces, displayTrueValue, displayFalseValue, aliases, categoryId.map(BSONObjectID(_)))
    }
    ((field: Field) => Some(
      field.name,
      field.label,
      field.fieldType,
      field.isArray,
      field.numValues,
      field.displayDecimalPlaces,
      field.displayTrueValue,
      field.displayFalseValue,
      field.aliases,
      field.categoryId.map(_.stringify))
    )
  )

  // router for requests; to be passed to views as helper.
  protected val router = new DictionaryRouter(dataSetId)
  protected val jsRouter = new DictionaryJsRouter(dataSetId)
  protected val dataViewRouter = new DataViewRouter(dataSetId)
  protected val dataViewJsRouter = new DataViewJsRouter(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  override protected def createView(f : Form[Field])(implicit msg: Messages, request: Request[_]) =
    dictionary.create(dataSetName + " Field", f, allCategories, router)

  override protected def showView(name: String, f : Form[Field])(implicit msg: Messages, request: Request[_]) =
    editView(name, f)

  override protected def editView(name: String, f : Form[Field])(implicit msg: Messages, request: Request[_]) =
    dictionary.edit(
      dataSetName + " Field",
      name,
      f,
      allCategories,
      router,
      result(dataSpaceTree)
    )

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
      dataViewRouter,
      dataViewJsRouter,
      fieldNameLabels,
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
    val futureMetaInfos = dataSpaceTree
    val futureItemsAndCount = getFutureItemsAndCount(page, orderBy, filter)

    {
      for {
        (items, count) <- futureItemsAndCount
        metaInfos <- futureMetaInfos
        fieldChartSpecs <- fieldChartSpecsFuture
        _ <- setCategoriesById(categoryRepo, items)
      } yield {
        val newFilter = filter.map { condition =>
          val label = fieldNameLabelMap.get(condition.fieldName.trim)
          condition.copy(fieldLabel = label.flatten)
        }
        Ok(overviewListView(
          Page(items, page, page * pageLimit, count, orderBy, Some(new models.Filter(newFilter))),
          fieldChartSpecs,
          metaInfos)
        )
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the dictionary list process")
        InternalServerError(t.getMessage)
    }
  }

  def inferDictionary = Action.async { implicit request =>
    // TODO: introduce type inference setting for each data set
    dataSetService.inferDictionaryAndUpdateRecords(dataSetId, 5,Seq(FieldTypeId.Json)).map( _ =>
      home.flashing("success" -> s"Dictionary for '${dataSetId}'  was successfully inferred.")
    )
  }

  override def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = {
    val eolToUse = eol match {
      case Some(eol) => if (eol.trim.nonEmpty) eol.trim else csvEOL
      case None => csvEOL
    }
    exportToCsv(
      csvFileName,
      delimiter,
      eolToUse,
      if (replaceEolWithSpace) csvCharReplacements else Nil)(
      Some(exportOrderByFieldName),
      filter,
      if (tableColumnsOnly) listViewColumns.get else Nil
    )
  }

  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  override def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) =
    exportToJson(
      jsonFileName)(
      Some(exportOrderByFieldName),
      filter,
      if (tableColumnsOnly) listViewColumns.get else Nil
    )

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
    toCriteria(filter).flatMap { criteria =>
      repo.find(
        criteria = criteria,
        projection = Seq(fieldName)
      ).map { fields =>
        val values = fields.map(field => Some(fieldExtractor(field).toString))
        ChartSpec.categorical(values, None, chartTitle, false, true)
      }
    }

  private def dataSpaceTree =
    DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)
}