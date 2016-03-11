package controllers.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import controllers.CrudController
import models.DataSetFormattersAndIds._
import models.{D3Node, Page, Category}
import persistence.AscSort
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Action, RequestHeader}
import reactivemongo.bson.BSONObjectID
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.json.BSONObjectIDFormat
import views.html.category
import scala.concurrent.duration._

import scala.concurrent.Await.result

trait CategoryControllerFactory {
  def apply(dataSetId: String): CategoryController
}

protected[controllers] class CategoryControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends CrudController[Category, BSONObjectID](dsaf(dataSetId).get.categoryRepo) with CategoryController {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected lazy val dataSetName = result(dsa.metaInfo).name
  protected lazy val fieldRepo = dsa.fieldRepo

  protected override val listViewColumns = Some(Seq("name"))

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "parentId" -> optional(nonEmptyText)
    ) { (id, name, parentId) =>
      Category(id, name, parentId.map(BSONObjectID(_)))
    }
    ((category: Category) => Some(category._id, category.name, category.parentId.map(_.stringify)))
  )

  // router for requests; to be passed to views as helper.
  protected lazy val router: CategoryRouter = new CategoryRouter(dataSetId)

  // field router
  protected lazy val fieldRouter: DictionaryRouter = new DictionaryRouter(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  override protected def createView(f : Form[Category])(implicit msg: Messages, request: RequestHeader) =
    category.create(dataSetName + " Category", f, allCategories, router)

  override protected def showView(id: BSONObjectID, f : Form[Category])(implicit msg: Messages, request: RequestHeader) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[Category])(implicit msg: Messages, request: RequestHeader) = {
    val fieldsFuture = fieldRepo.find(Some(Json.obj("categoryId" -> id)), Some(Seq(AscSort("name"))))
    val fields = result(fieldsFuture)
    category.edit(dataSetName + " Category", id, f, allCategories, fields, router, fieldRouter.get)
  }

  override protected def listView(page: Page[Category])(implicit msg: Messages, request: RequestHeader) = {
    val futureMetaInfos = dataSetMetaInfoRepo.find()

    category.list(
      dataSetName + " Category",
      page,
      router,
      result(futureMetaInfos)
    )
  }

  override def getCategoryD3Root = Action { implicit request =>
    val categories = allCategories

    val idD3NodeMap = categories.map(category => (category._id.get, D3Node(category.name, None))).toMap

    categories.foreach { category =>
      if (category.parentId.isDefined) {
        val node = idD3NodeMap(category._id.get)
        val parent = idD3NodeMap(category.parentId.get)
        parent.children = parent.children ++ Seq(node)
      }
    }

    val layerOneCategories = categories.filter(_.parentId.isEmpty)

    val root = D3Node("Root", None, layerOneCategories.map(category => idD3NodeMap(category._id.get)).toSeq)

    Ok(Json.toJson(root))
  }

  // TODO: change to an async call
  protected def allCategories = {
    val categoriesFuture = repo.find(None, Some(Seq(AscSort("name"))))
//    val categoriesFuture = repo.find().map(_.toSeq.sortBy(_.name))
    result(categoriesFuture)
  }

  override protected val defaultCreateEntity =
    Category(None, "")
}