package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import controllers.{CrudController, EnumFormatter, MapJsonFormatter}
import models._
import models.DataSetFormattersAndIds._
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, RequestHeader}
import reactivemongo.bson.BSONObjectID
import util.{ChartSpec, FieldChartSpec, FilterSpec}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import views.html.dictionary
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

trait DictionaryControllerFactory {
  def apply(dataSetId: String): DictionaryController
}

class DictionaryControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends CrudController[Field, String](dsaf(dataSetId).get.dictionaryFieldRepo) with DictionaryController {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected lazy val dataSetName = Await.result(dsa.metaInfo, 120000 millis).name

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
      "label" ->  optional(nonEmptyText),
      "categoryId" -> ignored(Option.empty[BSONObjectID]),
      "category" -> ignored(Option.empty[Category])
    )(Field.apply)(Field.unapply))

  // router for requests; to be passed to views as helper.
  protected lazy val router: DictionaryRouter = DictionaryRouter(dataSetId)

  override protected lazy val home =
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

  private def overviewListView(
    page: Page[Field],
    fieldChartSpecs : Iterable[FieldChartSpec],
    dataSetMetaInfos: Traversable[DataSetMetaInfo]
  )(implicit msg: Messages, request: RequestHeader) =
    dictionary.list(
      dataSetName + " Field",
      page,
      fieldChartSpecs,
      router,
      dataSetMetaInfos,
      6
    )

  override def overviewList(page: Int, orderBy: String, filter: FilterSpec) = Action.async { implicit request =>
    val fieldNameExtractors = Seq(
      ("Field Type", "fieldType", (field : Field) => field.fieldType)
//      ("Enum", "isEnum", (field : Field) => field.isEnum)
    )

    val futureFieldChartSpecs = fieldNameExtractors.map { case (title, fieldName, fieldExtractor) =>
      getDictionaryChartSpec(title, filter.toJsonCriteria, fieldName, fieldExtractor).map(chartSpec => FieldChartSpec(fieldName, chartSpec))
    }

    val futureMetaInfos = dataSetMetaInfoRepo.find()

    val (futureItems, futureCount) = getFutureItemsAndCount(page, orderBy, filter)

    futureItems.zip(futureCount).zip(futureMetaInfos).zip(Future.sequence(futureFieldChartSpecs)).map{
      case (((items, count), metaInfos),fieldChartSpecs) => {
        implicit val msg = messagesApi.preferred(request)
        Ok(overviewListView(Page(items, page, page * limit, count, orderBy, filter), fieldChartSpecs, metaInfos))
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

  override protected val defaultCreateEntity =
    new Field("", FieldType.Null)
}
