package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.security.AdaAuthConfig
import com.google.inject.assistedinject.Assisted
import controllers.{DataSetWebContext, JsonFormatter}
import dataaccess.RepoTypes.{DataSpaceMetaInfoRepo, UserRepo}
import dataaccess._
import models._
import models.FilterCondition.{FilterIdentity, filterFormat}
import models.security.{SecurityRole, UserManager}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import java.util.Date

import reactivemongo.play.json.BSONFormats._
import controllers.core.{CrudControllerImpl, HasFormShowEqualEditView, WebContext}
import services.DataSpaceService
import views.html.{dataview, filters => view}

import scala.concurrent.Future

trait FilterControllerFactory {
  def apply(dataSetId: String): FilterController
}

protected[controllers] class FilterControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService,
    userRepo: UserRepo,
    val userManager: UserManager
  ) extends CrudControllerImpl[Filter, BSONObjectID](dsaf(dataSetId).get.filterRepo)

    with FilterController
    with AdaAuthConfig
    with HasFormShowEqualEditView[Filter, BSONObjectID] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected val filterRepo = dsa.filterRepo

  override protected val listViewColumns = None // Some(Seq("name", "conditions"))
  override protected val entityNameKey = "filter"
  override protected def formatId(id: BSONObjectID) = id.stringify

  private implicit val filterConditionFormatter = JsonFormatter[FilterCondition]

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "conditions" -> seq(of[FilterCondition]),
      "isPrivate" -> boolean
    ) { (id, name, conditions, isPrivate) =>
      Filter(id, Some(name), conditions, isPrivate)
    }
    ((item: Filter) => Some(item._id, item.name.getOrElse(""), item.conditions, item.isPrivate))
  )

  protected val router: FilterRouter = new FilterRouter(dataSetId)
  protected val jsRouter: FilterJsRouter = new FilterJsRouter(dataSetId)

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  // create view and data

  override protected type CreateViewData = (String, Form[Filter])

  override protected def getFormCreateViewData(form: Form[Filter]) =
    for {
      dataSetName <- dsa.dataSetName
    } yield
      (dataSetName + " Filter", form)

  override protected[controllers] def createView = { implicit ctx =>
    (view.create(_, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    String,
    BSONObjectID,
    Form[Filter],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[Filter]
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)

    val setCreatedByFuture =
      form.value match {
        case Some(filter) => FilterRepo.setCreatedBy(userRepo, Seq(filter))
        case None => Future(())
      }

    for {
      // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space tree
      tree <- treeFuture

      // set the "created by" field for the filter
      _ <- setCreatedByFuture
    } yield
      (dataSetName + " Filter", id, form, tree)
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.edit(_, _, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    Page[Filter],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[Filter]
  ) = { request =>
    val setCreatedByFuture = FilterRepo.setCreatedBy(userRepo, page.items)
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val dataSetNameFuture = dsa.dataSetName

    for {
      // set created by
      _ <- setCreatedByFuture

      // get the data space tree
      tree <- treeFuture

      // get the data set name
      dataSetName <- dataSetNameFuture
    } yield
      (dataSetName + " Filter", page, tree)
  }

  override protected[controllers] def listView = { implicit ctx =>
    (view.list(_, _, _)).tupled
  }

  override def saveCall(
    filter: Filter)(
    implicit request: Request[AnyContent]
  ): Future[BSONObjectID] =
    for {
      user <- currentUser(request)
      id <- {
        val filterWithUser = user match {
          case Some(user) => filter.copy(timeCreated = Some(new Date()), createdById = user._id)
          case None => throw new AdaException("No logged user found")
        }
        repo.save(filterWithUser)
      }
    } yield
      id

  override def saveAjax(filter: Filter) = Action.async { implicit request =>
    saveCall(filter).map { id =>
      Ok(s"Item ${id} has been created")
    }.recover {
      case e: AdaException =>
        Logger.error("Problem found while executing the save function")
        BadRequest(e.getMessage)
      case t: TimeoutException =>
        Logger.error("Problem found while executing the save function")
        InternalServerError(t.getMessage)
      case i: RepoException =>
        Logger.error("Problem found while executing the save function")
        InternalServerError(i.getMessage)
    }
  }

  override protected def updateCall(
    filter: Filter)(
    implicit request: Request[AnyContent]
  ): Future[BSONObjectID] =
    for {
      existingFilterOption <- repo.get(filter._id.get)
      id <- {
        val mergedFilter =
          existingFilterOption.fold(filter) { existingFilter =>
            filter.copy(
              createdById = existingFilter.createdById,
              timeCreated = existingFilter.timeCreated,
              conditions = existingFilter.conditions
            )
          }
        repo.update(mergedFilter)
      }
    } yield
      id

  override def idAndNames = Action.async { implicit request =>
    for {
      filters <- filterRepo.find(
        sort = Seq(AscSort("name")),
        projection = Seq("name")
      )
    } yield
      Ok(Json.toJson(filters))
  }

  override def idAndNamesAccessible = Action.async { implicit request =>
    // auxiliary function to find filter for given criteria
    def findAux(criteria: Seq[Criterion[Any]]) = repo.find(
      criteria = criteria,
      projection = Seq("name", "isPrivate", "createdById"),
      sort = Seq(AscSort("name"))
    )

    for {
      user <- currentUser(request)

      filters <- {
        user match {
          case None => Future(Nil)

          case Some(user) =>
            val isAdmin = user.roles.contains(SecurityRole.admin)
            // admin     => get all; non-admin => not private or owner
            if (isAdmin)
              findAux(Nil)
            else
              findAux(Nil).map { filters =>
                filters.filter { filter =>
                  !filter.isPrivate || (filter.createdById.isDefined && filter.createdById.equals(user._id))
                }
              }
        }
      }
    } yield {
      val idAndNames = filters.toSeq.map( filter =>
        Json.obj("_id" -> filter._id, "name" -> filter.name)
      )
      Ok(JsArray(idAndNames))
    }
  }
}