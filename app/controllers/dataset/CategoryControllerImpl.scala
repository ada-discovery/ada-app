package controllers.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import controllers.CrudController
import models.DataSetFormattersAndIds._
import models.{D3Node, Page, Category}
import persistence.AscSort
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Action, RequestHeader}
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONObjectIDFormat
import views.html.category
import scala.concurrent.Future

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
  protected lazy val jsRouter: CategoryJsRouter = new CategoryJsRouter(dataSetId)

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

  override protected def deleteCall(id: BSONObjectID): Future[Unit] = {
    // relocate the children to a new parent
    val updateChildrenFutures =
      for {
        Some(category) <- repo.get(id)
        children <- repo.find(Some(Json.obj("parentId" -> id)))
      } yield
        children.map{ child =>
          child.parentId = category.parentId
          repo.update(child)
        }

    // remove the field category refs
    val updateFieldFutures =
      for {
        fields <- fieldRepo.find(Some(Json.obj("categoryId" -> id)))
      } yield
        fields.map { field =>
          field.categoryId = None
          fieldRepo.update(field)
        }

    // finally, combine all the futures and delete the category
    for {
      updateFutures1 <- updateChildrenFutures
      updateFutures2 <- updateFieldFutures
      _ <- Future.sequence(updateFutures1)
      _ <- Future.sequence(updateFutures2)
    } yield
      repo.delete(id)
  }

  override def getCategoryD3Root = Action { implicit request =>
    val categories = allCategories

    val idD3NodeMap = categories.map(category => (category._id.get, D3Node(category._id, category.name, None))).toMap

    categories.foreach { category =>
      if (category.parentId.isDefined) {
        val node = idD3NodeMap(category._id.get)
        val parent = idD3NodeMap(category.parentId.get)
        parent.children = parent.children ++ Seq(node)
      }
    }

    val layerOneCategories = categories.filter(_.parentId.isEmpty)

    val root = D3Node(None, "Root", None, layerOneCategories.map(category => idD3NodeMap(category._id.get)).toSeq)

    Ok(Json.toJson(root))
  }

  override def relocateToParent(id: BSONObjectID, parentId: Option[BSONObjectID]) = Action.async{ implicit request =>
    getCall(id).flatMap{ category =>
      if (category.isEmpty)
        Future(notFoundCategory(id))
      else if (parentId.isDefined)
        getCall(parentId.get).flatMap { parent =>
          if (parent.isEmpty)
            Future(notFoundCategory(parentId.get))
          else {
            category.get.parentId = parent.get._id
            repo.update(category.get).map(_ => Ok("Done"))
          }
        }
      else {
        category.get.parentId = None
        repo.update(category.get).map(_ => Ok("Done"))
      }
    }
  }

  private def notFoundCategory(id: BSONObjectID) =
    NotFound(s"Category with id #${id.stringify} not found. It has been probably deleted (by a different user). It's highly recommended to refresh your screen.")

  override def jsRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        jsRouter.get,
        jsRouter.relocateToParent
      )
    ).as("text/javascript")
  }

  // TODO: change to an async call
  protected def allCategories = {
    val categoriesFuture = repo.find(None, Some(Seq(AscSort("name"))))
    result(categoriesFuture)
  }

  override protected val defaultCreateEntity =
    Category(None, "")
}