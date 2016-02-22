package controllers

import java.util.concurrent.TimeoutException

import models._
import models.Dictionary._
import persistence.{DescSort, AscSort, DictionaryFieldRepo}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, optional, seq, nonEmptyText, of, boolean}
import play.api.i18n.Messages
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.{AnyContent, Action, RequestHeader}
import util.{ChartSpec, FilterSpec, FieldChartSpec}
import views.html.dictionary
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

trait DictionaryController {

  def find(page: Int, orderBy: String, filter: FilterSpec): Action[AnyContent]

  def listAll(orderBy: Int): Action[AnyContent]

  def get(id: String): Action[AnyContent]

  def create: Action[AnyContent]

  def edit(id: String): Action[AnyContent]

  def save: Action[AnyContent]

  def update(id: String): Action[AnyContent]

  def delete(id: String): Action[AnyContent]
}

abstract class DictionaryControllerImpl (
    repo: DictionaryFieldRepo
  ) extends CrudController[Field, String](repo) with DictionaryController {

  protected def dataSetName : String

  protected override val listViewColumns = Some(Seq("name", "fieldType", "label"))

  implicit val fieldTypeFormatter = EnumFormatter(FieldType)
  implicit val mapFormatter = MapJsonFormatter.apply

  override protected val form = Form(
    mapping(
      "name" -> nonEmptyText,
      "fieldType" -> of[FieldType.Value],
      "isArray" -> boolean,
      "numValues" -> optional(of[Map[String, String]]),
      "aliases" ->  seq(nonEmptyText),
      "label" ->  optional(nonEmptyText)
    )(Field.apply)(Field.unapply))

  protected def router : DictionaryRouter

  override protected val home =
    Redirect(router.plainList)

  override protected def createView(f : Form[Field])(implicit msg: Messages, request: RequestHeader) =
    dictionary.create(dataSetName + " Field", f, router)

  override protected def showView(name: String, f : Form[Field])(implicit msg: Messages, request: RequestHeader) =
    editView(name, f)

  override protected def editView(name: String, f : Form[Field])(implicit msg: Messages, request: RequestHeader) =
    dictionary.edit(dataSetName + " Field", name, f, router)

  // TODO: Remove
  override protected def listView(page: Page[Field])(implicit msg: Messages, request: RequestHeader) =
    throw new IllegalAccessException("List not implemented... used overviewList instead.")

  private def overviewListView(page: Page[Field], fieldChartSpecs : Iterable[FieldChartSpec])(implicit msg: Messages, request: RequestHeader) =
    dictionary.list(
      dataSetName + " Field",
      page,
      fieldChartSpecs,
      router,
      6
    )

  def overviewList(page: Int, orderBy: String, filter: FilterSpec) = Action.async { implicit request =>
    val fieldNameExtractors = Seq(
      ("Field Type", "fieldType", (field : Field) => field.fieldType)
//      ("Enum", "isEnum", (field : Field) => field.isEnum)
    )

    val futureFieldChartSpecs = fieldNameExtractors.map { case (title, fieldName, fieldExtractor) =>
      getDictionaryChartSpec(title, filter.toJsonCriteria, fieldName, fieldExtractor).map(chartSpec => FieldChartSpec(fieldName, chartSpec))
    }

    val (futureItems, futureCount) = getFutureItemsAndCount(page, orderBy, filter)

    futureItems.zip(futureCount).zip(Future.sequence(futureFieldChartSpecs)).map{
      case ((items, count), fieldChartSpecs) => {
        implicit val msg = messagesApi.preferred(request)
        Ok(overviewListView(Page(items, page, page * limit, count, orderBy, filter), fieldChartSpecs))
      }}.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the dictionary list process")
        InternalServerError(t.getMessage)
    }
  }

  private def getDictionaryChartSpec(
    chartTitle : String,
    criteria : Option[JsObject],
    fieldName : String,
    fieldExtractor : Field => Any
  ) : Future[ChartSpec] =
    repo.find(criteria, None, Some(Json.obj(fieldName -> 1))).map { fields =>
      val values = fields.map(fieldExtractor)
      ChartSpec.pie(values, None, chartTitle, false, true)
    }

  def getFieldNames = Action.async { implicit request =>
    val futureFieldNames = repo.find(None, Some(Seq(AscSort("name")))).map(_.map(_.name))
    futureFieldNames.map(fieldNames => Ok(Json.toJson(fieldNames)))
  }

  override protected val defaultCreateEntity =
    new Field("", FieldType.Null)
}