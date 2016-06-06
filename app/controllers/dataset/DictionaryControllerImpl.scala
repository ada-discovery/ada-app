package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import controllers.{CrudControllerImpl, EnumFormatter, MapJsonFormatter}
import models._
import models.DataSetFormattersAndIds._
import persistence.AscSort
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import persistence.dataset.DictionaryFieldRepo._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, RequestHeader, Request}
import reactivemongo.bson.BSONObjectID
import services.{DeNoPaSetting, DataSetService}
import util.{ChartSpec, FieldChartSpec, FilterSpec}
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
  ) extends CrudControllerImpl[Field, String](dsaf(dataSetId).get.fieldRepo) with DictionaryController {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get
  protected val categoryRepo: DictionaryCategoryRepo = dsa.categoryRepo
  protected lazy val dataSetName = result(dsa.metaInfo).name

  protected override val listViewColumns = Some(Seq("name", "fieldType", "label", "category"))

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
  protected lazy val router: DictionaryRouter = new DictionaryRouter(dataSetId)

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
    val categoriesFuture = categoryRepo.find(None, Some(Seq(AscSort("name"))))
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

  override def overviewList(page: Int, orderBy: String, filter: FilterSpec) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val fieldNameExtractors = Seq(
      ("Field Type", "fieldType", (field : Field) => field.fieldType)
//      ("Enum", "isEnum", (field : Field) => field.isEnum)
    )
    val futureFieldChartSpecs = fieldNameExtractors.map { case (title, fieldName, fieldExtractor) =>
      getDictionaryChartSpec(title, filter.toJsonCriteria, fieldName, fieldExtractor).map(chartSpec => FieldChartSpec(fieldName, chartSpec))
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
        Ok(overviewListView(Page(items, page, page * limit, count, orderBy, filter), fieldChartSpecs, metaInfos))
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

  private def getDictionaryChartSpec(
    chartTitle : String,
    criteria : Option[JsObject],
    fieldName : String,
    fieldExtractor : Field => Any
  ) : Future[ChartSpec] =
    repo.find(criteria, None, Some(Json.obj(fieldName -> 1))).map { fields =>
      val values = fields.map(field => Some(fieldExtractor(field).toString))
      ChartSpec.pie(values, None, chartTitle, false, true)
    }

  override protected val defaultCreateEntity =
    new Field("", FieldType.Null)
}