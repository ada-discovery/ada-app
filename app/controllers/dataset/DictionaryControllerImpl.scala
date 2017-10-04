package controllers.dataset

import javax.inject.Inject

import models.DistributionWidgetSpec

import scala.reflect.runtime.universe.TypeTag
import com.google.inject.assistedinject.Assisted
import controllers._
import controllers.core._
import dataaccess.RepoTypes.CategoryRepo
import dataaccess._
import dataaccess.Criterion._
import models._
import models.DataSetFormattersAndIds._
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import dataaccess.FieldRepo._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, Request}
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import services._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.FieldUtil
import views.html.{dataview, dictionary => view}

import scala.concurrent.Future

trait DictionaryControllerFactory {
  def apply(dataSetId: String): DictionaryController
}

protected[controllers] class DictionaryControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService,
    statsService: StatsService,
    dataSpaceService: DataSpaceService,
    val wgs: WidgetGenerationService
  ) extends CrudControllerImpl[Field, String](dsaf(dataSetId).get.fieldRepo)

    with DictionaryController
    with ExportableAction[Field]
    with WidgetRepoController[Field]
    with HasFormShowEqualEditView[Field, String] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get
  protected val categoryRepo: CategoryRepo = dsa.categoryRepo

  protected override val listViewColumns = Some(Seq("name", "fieldType", "isArray", "label", "categoryId"))

  private val exportOrderByFieldName = "name"
  private val csvFileName = "dictionary_" + dataSetId.replace(" ", "-") + ".csv"
  private val jsonFileName = "dictionary_" + dataSetId.replace(" ", "-") + ".json"

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  implicit val fieldTypeFormatter = EnumFormatter(FieldTypeId)
  implicit val mapFormatter = MapJsonFormatter.apply

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  override protected val typeTag = implicitly[TypeTag[Field]]
  override protected val format = fieldFormat
  override protected val excludedFieldNames = Set("category", "numValues")

  private val fieldNameLabels = Seq(
    ("fieldType", Some("Field Type")),
    ("isArray", Some("Is Array?")),
    ("label", Some("Label")),
    ("name", Some("Name"))
  )
  private val fieldNameLabelMap = fieldNameLabels.toMap

  override protected[controllers] val form = Form(
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

  protected val router = new DictionaryRouter(dataSetId)
  protected val jsRouter = new DictionaryJsRouter(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  // create view and  data

  override protected type CreateViewData = (String, Form[Field], Traversable[Category])

  override protected def getFormCreateViewData(form: Form[Field]) = {
    val dataSetNameFuture = dsa.dataSetName
    val categoriesFuture = allCategoriesFuture

    for {
      // get the data set name
      dataSetName <- dataSetNameFuture

      // get all the categories
      allCategories <- categoriesFuture
    } yield
      (dataSetName + " Field", form, allCategories)
  }

  override protected[controllers] def createView = { implicit ctx =>
    (view.create(_, _, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    String,
    String,
    Form[Field],
    Traversable[Category],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getFormEditViewData(
    id: String,
    form: Form[Field]
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val categoriesFuture = allCategoriesFuture
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)

    for {
      // get the data set name
      dataSetName <- dataSetNameFuture

      // retrieve all the categories
      allCategories <- categoriesFuture

      // get the data space tree
      tree <- treeFuture
    } yield
      (dataSetName + " Field", id, form, allCategories, tree)
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.edit(_, _, _, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    Page[Field],
    Traversable[Widget],
    Traversable[(String, Option[String])],
    Traversable[DataSpaceMetaInfo]
  )

  private val widgetSpecs = Seq(
    DistributionWidgetSpec("fieldType", None)
  )

  override protected def getListViewData(page: Page[Field]) = { request =>
    val newConditions = page.filterConditions.map { condition =>
      val label = fieldNameLabelMap.get(condition.fieldName.trim)
      condition.copy(fieldLabel = label.flatten)
    }

    val newPage = page.copy(filter = Some(new models.Filter(newConditions)))

    // create futures as vals so they are executed in parallel
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)

    val nameFuture = dsa.dataSetName

    val widgetsFuture = toCriteria(newConditions).flatMap( criteria =>
      widgets(widgetSpecs, criteria)
    )

    val setCategoriesFuture = setCategoriesById(categoryRepo, newPage.items)

    for {
      // get the data space tree
      tree <- treeFuture

      // get the data set name
      dataSetName <- nameFuture

      // create widgets
      widgets <- widgetsFuture

      // set categories
      _ <- setCategoriesFuture
    } yield
      (dataSetName + " Field", newPage, widgets.flatten, fieldNameLabels, tree)
  }

  override protected[controllers] def listView = { implicit ctx =>
    (view.list(_, _, _, _, _)).tupled
  }

  // actions

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
      if (tableColumnsOnly) listViewColumns.get else Nil,
      listViewColumns
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

  protected def allCategoriesFuture =
    categoryRepo.find(sort = Seq(AscSort("name")))

  override protected def filterValueConverters(
    fieldNames: Traversable[String]
  ): Future[Map[String, String => Option[Any]]] =
    for {
      fields <- fieldCaseClassRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSeq))
    } yield
      FieldUtil.valueConverters(fields)
}