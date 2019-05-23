package controllers.requests

import java.util.Date

import javax.inject.Inject
import models.BatchOrderRequest.batchRequestFormat
import models.BatchOrderRequest.historyFormat
import models.BatchOrderRequest.actionInfoFormat
import models.BatchOrderRequest.requestActionFormat
import models.{ActionInfo, BatchOrderRequest, BatchRequestAction, BatchRequestState, TrackingHistory}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.services.UserManager
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters.{EnumFormatter, JsonFormatter}
import org.incal.play.security.SecurityRole
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo
import services.request.RequestStatusService

import scala.concurrent.Future
import scala.util.parsing.json.JSONFormat

@Deprecated
class BatchOrderRequestsController @Inject()(
                                              requestsRepo: BatchOrderRequestRepo,
                                              userRepo: UserRepo,
                                              val userManager: UserManager,
                                              val statusService: RequestStatusService
                                            ) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicFormCrudViews[BatchOrderRequest, BSONObjectID]
  with AdaAuthConfig {

  private implicit val hostoryFormatter = JsonFormatter[TrackingHistory]
  private implicit val requestStateFormatter = EnumFormatter(BatchRequestState)


  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "itemIds" -> nonEmptyText,
      "state" -> of[BatchRequestState.Value],
      "created by id" -> ignored(Option.empty[BSONObjectID]),
      "created by name" -> ignored(Option.empty[String]),
      "date" -> ignored(new Date()),
      "history"->ignored(Option.empty[TrackingHistory])
    )(BatchOrderRequest.apply)(BatchOrderRequest.unapply))
  override protected val homeCall = routes.BatchOrderRequestsController.find()

  override def saveCall(
                         batchRequest: BatchOrderRequest)(
                         implicit request: Request[AnyContent]
                       ): Future[BSONObjectID] =
  {
    for {
      user <- currentUser(request)
      id <- {
        val batchRequestWithUser = user match {
          case Some(user) =>
            val date = new Date()
            val newState = BatchRequestState.Created
            val requestAction = buildBatchRequestAction(user._id,newState,newState)
            val actionInfo = buildActionInfo(date,requestAction)
            val newHistory = buildHistory(None, date, user._id, actionInfo)
            batchRequest.copy(timeCreated = date, createdById = user._id, state = newState, history = newHistory)
            //batchRequest.copy(timeCreated = new Date(), createdById = user._id, state = BatchRequestState.Created)
          case None => throw new AdaException("No logged user found")
        }
        repo.save(batchRequestWithUser)
      }
    } yield
      id
}

  def buildHistory(currentHistory: Option[TrackingHistory], date: Date, userId: Option[BSONObjectID], actionInfo:ActionInfo):Option[TrackingHistory] ={
    currentHistory match {
    case Some(currentHistory) => {
      Some(currentHistory.copy(actionInfo=actionInfo::currentHistory.actionInfo))
    }
    case None => Some(TrackingHistory(List(actionInfo)))
  }
  }

  def buildActionInfo(date: Date,requestAction:BatchRequestAction):ActionInfo={
    ActionInfo(date, requestAction, None)
  }

  def buildBatchRequestAction(userId: Option[BSONObjectID],fromState:BatchRequestState.Value, toState: BatchRequestState.Value)={
  BatchRequestAction(userId.get,fromState,toState)
}

  override def updateCall(
                         batchRequest: BatchOrderRequest)(
                         implicit request: Request[AnyContent]
                       ): Future[BSONObjectID] =
  {
    for {
      existingRequest <- repo.get(batchRequest._id.get)
      user <- currentUser(request)
      id <- {
        val batchRequestWithUser = user match {
          case Some(user) =>
            val newState = getState(existingRequest.get.state,batchRequest.state)
            val requestAction = buildBatchRequestAction(user._id,existingRequest.get.state,newState)
            var actionInfo = buildActionInfo(new Date(),requestAction)

            batchRequest.copy(state = newState,createdById = existingRequest.get.createdById, history = buildHistory(existingRequest.get.history,new Date(),user._id,actionInfo))
          case None => throw new AdaException("No logged user found")
        }
        repo.update(batchRequestWithUser)
      }

    } yield
      id
  }


  def getState(currentState: BatchRequestState.Value, updatedState: BatchRequestState.Value):BatchRequestState.Value={
    if(currentState==updatedState){
      currentState
    } else
    {
      statusService.getNextStates(currentState).contains((updatedState.toString,updatedState.toString)) match {
        case true =>
          updatedState
        case false => throw new AdaException("Status provided "+updatedState+" not allowed for current status " + currentState)
      }
    }
  }

  override protected def getListViewData(page: Page[BatchOrderRequest], conditions: Seq[FilterCondition]) = { request =>
    for {
     users<-getUsers(page.items)
    } yield
      (page.copy(items = buildItemsWithUserName(page.items,users)),conditions)
  }

  def getUsers(requests: Traversable[BatchOrderRequest])={
    val userIds =requests.map(_.createdById).flatten.map(Some(_)).toSeq

    userRepo.find(Seq(BatchOrderRequest.BatchRequestIdentity.name #-> userIds)).map { users =>
      users.map(c => (c._id.get, c)).toMap
    }
  }

  def buildItemsWithUserName(requests: Traversable[BatchOrderRequest],users:Map[BSONObjectID,User]) = {
      requests.map(r=>buildItemWithName(r, users.get(r.createdById.get)))
  }

  def buildItemWithName(request:BatchOrderRequest,user:Option[User])= {
    request.copy(createdByName = Some(user.get.ldapDn))
  }

  def isAdmin(context: WebContext)={
    for {
      user <- currentUser(context.request)
    } yield {
      user.get.roles.contains(SecurityRole.admin)
    }
  }


  override protected def createView = { implicit ctx => views.html.requests.create(_) }
  override protected def showView = editView

  override protected def editView = { implicit ctx =>
    if(true==true){
      views.html.requests.actions(_, statusService)
    } else {
      views.html.requests.edit(_)
    }
    }

  override protected def listView = { implicit ctx =>
    (views.html.requests.list(_, _)).tupled }
}