package controllers.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import controllers.CrudControllerImpl
import dataaccess.{AscSort, Criterion}
import dataaccess.Criterion.Infix
import models.{JsTreeNode, Category, D3Node, Page}
import models.DataSetFormattersAndIds._
import Criterion.Infix
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import persistence.dataset.{DataSpaceMetaInfoRepo, DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Action, RequestHeader, Request}
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import views.html.category
import scala.concurrent.Future

trait CategoryControllerFactory {
  def apply(dataSetId: String): CategoryController
}

protected[controllers] class CategoryControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends CrudControllerImpl[Category, BSONObjectID](dsaf(dataSetId).get.categoryRepo) with CategoryController {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected lazy val dataSetName = result(dsa.metaInfo).name
  protected lazy val fieldRepo = dsa.fieldRepo

  protected override val listViewColumns = Some(Seq(CategoryIdentity.name, "name"))

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "label" -> optional(nonEmptyText),
      "parentId" -> optional(nonEmptyText)
    ) { (id, name, label, parentId) =>
      Category(id, name, label, parentId.map(BSONObjectID(_)))
    }
    ((category: Category) => Some(category._id, category.name, category.label, category.parentId.map(_.stringify)))
  )

  // router for requests; to be passed to views as helper.
  protected lazy val router = new CategoryRouter(dataSetId)
  protected lazy val jsRouter = new CategoryJsRouter(dataSetId)
  protected lazy val dataSetRouter = new DataSetRouter(dataSetId)

  // field router
  protected lazy val fieldRouter: DictionaryRouter = new DictionaryRouter(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  override protected def createView(f : Form[Category])(implicit msg: Messages, request: Request[_]) =
    category.create(dataSetName + " Category", f, allCategories, router)

  override protected def showView(id: BSONObjectID, f : Form[Category])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[Category])(implicit msg: Messages, request: Request[_]) = {
    val fieldsFuture = fieldRepo.find(
      criteria = Seq("categoryId" #== Some(id)),
      sort = Seq(AscSort("name"))
    )

    val fields = result(fieldsFuture)
    category.edit(
      dataSetName + " Category",
      id,
      f,
      allCategories,
      fields,
      router,
      dataSetRouter,
      fieldRouter.get,
      result(dsa.setting.map(_.filterShowFieldStyle)),
      result(dataSpaceTree)
    )
  }

  override protected def listView(page: Page[Category])(implicit msg: Messages, request: Request[_]) =
    category.list(
      dataSetName + " Category",
      page,
      router,
      result(dataSpaceTree)
    )

  override protected def deleteCall(id: BSONObjectID)(implicit request: Request[AnyContent]): Future[Unit] = {
    // relocate the children to a new parent
    val updateChildrenFutures =
      for {
        Some(category) <- repo.get(id)
        children <- repo.find(Seq("parentId" #== Some(id)))
      } yield
        children.map{ child =>
          child.parentId = category.parentId
          repo.update(child)
        }

    // remove the field category refs
    val updateFieldFutures =
      for {
        fields <- fieldRepo.find(Seq("categoryId" #== Some(id)))
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

  override protected def updateCall(category: Category)(implicit request: Request[AnyContent]) =
    for {
      // collect the fields previously associated with a category
      oldFields <- fieldRepo.find(Seq("categoryId" #== category._id))

      // disassociate the old fields
      _ <- {
        val disassociatedFields = oldFields.map(_.copy(categoryId = None))
        fieldRepo.update(disassociatedFields).map(_ => Some(()))
      }

      // colect the newly associated fileds
      newFields <- {
        val fieldNames = getParamMap(request).get("fields[]").getOrElse(Nil)
        fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))
      }

      // update them
      _ <- {
        val associatedFields = newFields.map(_.copy(categoryId = category._id))
        fieldRepo.update(associatedFields).map(_ => Some(()))
      }

      // update the category itself
      id <- repo.update(category)
    } yield
      id

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

  override def getCategoriesWithFieldsAsTreeNodes = Action.async { implicit request =>
    for {
      categories <- repo.find()
      fieldsWithCategory <- fieldRepo.find(Seq("categoryId" #!= None))
    } yield {
      val jsTreeNodes =
        categories.map(JsTreeNode.fromCategory) ++ fieldsWithCategory.map(JsTreeNode.fromField)
      Ok(Json.toJson(jsTreeNodes))
    }
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

  override def saveForName(name: String) = Action.async{ implicit request =>
    repo.save(Category(None, name)).map( id => Ok(Json.toJson(id)))
  }

  override def addFields(
    categoryId: BSONObjectID,
    fieldNames: Seq[String]
  ) = Action.async { implicit request =>
    for {
      category <- repo.get(categoryId)
      fields <- fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))
      response <- category match {
        case Some(category) => {
          val newFields = fields.map(_.copy(categoryId = Some(categoryId)))
          fieldRepo.update(newFields).map(_ => Some(()))
        }
        case None => Future(None)
      }
    } yield
      response.fold(
        NotFound(s"Category '#${categoryId.stringify}' not found")
      ) { _ => Ok("Done")}
  }

  override def idAndNames = Action.async { implicit request =>
    for {
      categories <- repo.find(
        sort = Seq(AscSort("name")),
        projection = Seq(CategoryIdentity.name, "name")
      )
    } yield {
      Ok(Json.toJson(categories))
    }
  }

  private def notFoundCategory(id: BSONObjectID) =
    NotFound(s"Category with id #${id.stringify} not found. It has been probably deleted (by a different user). It's highly recommended to refresh your screen.")

  override def jsRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("categoryJsRoutes")(
        jsRouter.get,
        jsRouter.relocateToParent,
        jsRouter.saveForName,
        jsRouter.addFields
      )
    ).as("text/javascript")
  }

  // TODO: change to an async call
  protected def allCategories = {
    val categoriesFuture = repo.find(sort = Seq(AscSort("name")))
    result(categoriesFuture)
  }

  private def dataSpaceTree =
    DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)
}