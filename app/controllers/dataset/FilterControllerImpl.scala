package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.security.AdaAuthConfig
import com.google.inject.assistedinject.Assisted
import controllers.{CrudControllerImpl, DataSetWebContext, JsonFormatter}
import dataaccess.RepoTypes.{DataSpaceMetaInfoRepo, UserRepo}
import dataaccess.{AscSort, Criterion, FilterRepo, RepoException}
import models._
import models.FilterCondition.{FilterIdentity, filterFormat}
import Criterion.Infix
import models.security.{SecurityRole, UserManager}
import reactivemongo.play.json.BSONFormats._
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory, DataSpaceMetaInfoRepo}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import reactivemongo.bson.BSONObjectID
import java.util.Date

import views.html.filters

import scala.concurrent.Future

trait FilterControllerFactory {
  def apply(dataSetId: String): FilterController
}

protected[controllers] class FilterControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    userRepo: UserRepo,
    val userManager: UserManager
) extends CrudControllerImpl[Filter, BSONObjectID](dsaf(dataSetId).get.filterRepo) with FilterController with AdaAuthConfig {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected lazy val dataSetName = result(dsa.metaInfo).name
  protected lazy val filterRepo = dsa.filterRepo

  protected override val listViewColumns = None // Some(Seq("name", "conditions"))

  private implicit val filterConditionFormatter = JsonFormatter[FilterCondition]

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "conditions" -> seq(of[FilterCondition])
    ) { (id, name, conditions) =>
      Filter(id, Some(name), conditions)
    }
    ((item: Filter) => Some(item._id, item.name.getOrElse(""), item.conditions))
  )

  protected val router: FilterRouter = new FilterRouter(dataSetId)
  protected val jsRouter: FilterJsRouter = new FilterJsRouter(dataSetId)

  private implicit def toWebContext(implicit request: Request[_]) = {
    implicit val msg = messagesApi.preferred(request)
    DataSetWebContext(dataSetId)
  }

  override protected lazy val home =
    Redirect(router.plainList)

  override protected def createView(f : Form[Filter])(implicit msg: Messages, request: Request[_]) =
    filters.create(dataSetName + " Filter", f)

  override protected def showView(id: BSONObjectID, f : Form[Filter])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[Filter])(implicit msg: Messages, request: Request[_]) = {
    // TODO: stay in the future
    // set created by
    if(f.value.isDefined)
      result(FilterRepo.setCreatedBy(userRepo, Seq(f.get)))

    filters.edit(
      dataSetName + " Filter",
      id,
      f,
      result(dataSpaceTree)
    )
  }

  override protected def listView(page: Page[Filter])(implicit msg: Messages, request: Request[_]) = {
    // TODO: stay in the future
    // set created by
    result(FilterRepo.setCreatedBy(userRepo, page.items))

    filters.list(
      dataSetName + " Filter",
      page,
      result(dataSpaceTree)
    )
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

  override def getIdAndNames = Action.async { implicit request =>
    for {
      filters <- filterRepo.find(sort = Seq(AscSort("name")))
    } yield {
      val idAndNames = filters.map( filter =>
        Json.obj("_id" -> filter._id, "name" -> filter.name)
      )
      Ok(Json.toJson(idAndNames))
    }
  }

  private def dataSpaceTree =
    DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)
}