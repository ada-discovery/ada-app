package controllers.requests

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.BatchOrderRequest.batchRequestFormat
import models.BatchOrderRequest.actionInfoFormat
import models.{Action, ActionInfo, ApprovalCommittee, BatchOrderRequest, BatchRequestState, NotificationInfo, RequestAction}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.User.UserIdentity
import org.ada.server.services.UserManager
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.{BSONObjectIDStringFormatter, routes}
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters.{EnumFormatter, JsonFormatter}
import org.incal.play.security.SecurityRole
import org.incal.play.security.SecurityUtil.restrictAdminAnyNoCaching
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{Action, AnyContent, Request, Result}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}
import services.request.{ActionGraph, ActionNotificationService}
import org.incal.core.dataaccess.Criterion._
import scala.concurrent.Future


@Deprecated
class BatchOrderRequestsController @Inject()(
                                       requestsRepo: BatchOrderRequestRepo,

                                              userRepo: UserRepo,
                                              committeeRepo: ApprovalCommitteeRepo,
                                              val userManager: UserManager,
                                              val actionNotificationService: ActionNotificationService,
                                              mailerClient: MailerClient
                                            ) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicFormCrudViews[BatchOrderRequest, BSONObjectID]
  with AdaAuthConfig {

  private implicit val idsFormatter = BSONObjectIDStringFormatter
  private implicit val actionInfoFormatter = JsonFormatter[Seq[ActionInfo]]
  private implicit val requestStateFormatter = EnumFormatter(BatchRequestState)
  private val requestsListRedirect = Redirect(routes.BatchOrderRequestsController.listAll())

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "itemIds" ->  seq(of[BSONObjectID]),
      "state" -> of[BatchRequestState.Value],
      "created by id" -> ignored(Option.empty[BSONObjectID]),
      "created by name" -> ignored(Option.empty[String]),
      "date" -> ignored(new Date()),
      "history"->ignored(Seq[ActionInfo]())
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
            val actionInfo = buildActionInfo(date,user._id,newState,newState,None)
            val newHistory = buildHistory(Seq(), date, user._id, actionInfo)
            batchRequest.copy(timeCreated = date, createdById = user._id, state = newState, history = newHistory)
          case None => throw new AdaException("No logged user found")
        }
        repo.save(batchRequestWithUser)
      }
    } yield
      id
}

  def buildHistory(currentHistory: Seq[ActionInfo], date: Date, userId: Option[BSONObjectID], actionInfo:ActionInfo):Seq[ActionInfo] ={
    currentHistory.isEmpty match {
    case false => {
      currentHistory :+ actionInfo
    }
    case true => List(actionInfo)
  }
  }

  def buildActionInfo(date: Date,userId: Option[BSONObjectID],fromState:BatchRequestState.Value, toState: BatchRequestState.Value, description: Option[String]):ActionInfo={
    ActionInfo(date, userId.get,fromState,toState, description)
  }


  def getUserIds(committeeIds: Traversable[ApprovalCommittee], existingRequest: Option[BatchOrderRequest]): Seq[BSONObjectID] = {
   val ids=committeeIds.flatMap(_.userIds).toSeq :+ existingRequest.get.createdById.get
  ids
  }


  def requestAction(requestId: BSONObjectID, action: RequestAction.Value, description: String)= restrictAdminAnyNoCaching(deadbolt){

    implicit request => {
      for {
        existingRequest <- repo.get(requestId)
        user <- currentUser(request)
        committeeIds <- committeeRepo.find(Seq("dataSetId" #== existingRequest.get.dataSetId))
        usersToNotify <- userRepo.find(Seq(UserIdentity.name #-> getUserIds(committeeIds,existingRequest).map(id => Some(id))))
        id <- {
          val batchRequestWithState = user match {
            case Some(updatedByUser) =>
              val dateOfUpdate = new Date()
              val newState: BatchRequestState.Value = getNextState(existingRequest.get.state, action)
              var actionInfo = buildActionInfo(dateOfUpdate, updatedByUser._id, existingRequest.get.state, newState, Some(description))
              var updatedHistory = buildHistory(existingRequest.get.history, dateOfUpdate, updatedByUser._id, actionInfo)

              usersToNotify.map(user => addNotification(buildNotification(existingRequest, user, newState, dateOfUpdate, updatedByUser)))
              existingRequest.get.copy(state = newState, createdById = existingRequest.get.createdById, history = updatedHistory)
            case None => throw new AdaException("No logged user found")
          }
          repo.update(batchRequestWithState)
        }

      } yield {
        id
        actionNotificationService.sendNotifications()
        requestsListRedirect.flashing("success" -> "state of request updated with success to: ")
      }
    }.recover(handleExceptions("request action"))
  }

  def getNextState(currentState: BatchRequestState.Value, action: RequestAction.Value):BatchRequestState.Value ={
      ActionGraph.apply.get(currentState).get.find(validAction=>validAction.action == action) match {
        case Some(allowedAction) =>  allowedAction.toState
        case None => throw new AdaException("Action '"+ action +"' not allowed for current state '" +currentState+"'")
      }
   }


  def addNotification(notification: NotificationInfo)={
       actionNotificationService.addNotification(notification)
  }

  def buildNotification(existingRequest: Option[BatchOrderRequest], targetUser: User, newState: BatchRequestState.Value, dateOfUpdate: Date, updatedByUser: User)={
  NotificationInfo(existingRequest.get._id.get,existingRequest.get.timeCreated,targetUser.ldapDn,
    targetUser.email,existingRequest.get.state,newState,dateOfUpdate,updatedByUser.ldapDn)
  }

  def addNotifications(existingRequest: Option[BatchOrderRequest], requesterUser: User, newState: BatchRequestState.Value, dateOfUpdate: Date, user: User) = {
    addNotification(buildNotification(existingRequest,requesterUser,newState,dateOfUpdate,user))

    println("searching committee")

    for {
      committeeUsersddd<- committeeRepo.find(projection = Seq("userIds"))
      //  committeeUsers <- userRepo.fin
    } yield {
      println("committee1")
      committeeUsersddd.map { users => println(users)}
    }
    println("searching committee")
    for {

     committeeIds <- committeeRepo.find(Seq("dataSetId" #== existingRequest.get.dataSetId))
   //  committeeUsers <- userRepo.fin
    } yield{


      committeeIds.flatMap(_.userIds).map(userId => addNotification(buildNotification(existingRequest,requesterUser,newState,dateOfUpdate,user)))
     //   actionNotificationService.addNotification(requesterNotification))



      println("committee2")
      println(committeeIds)
      println(committeeIds.take(1))





     // val committeeNotification=NotificationInfo(existingRequest.get._id.get,existingRequest.get.timeCreated,requesterUser.get.ldapDn,
      //  requesterUser.get.email,existingRequest.get.state,newState,dateOfUpdate,user.ldapDn)

      committeeIds
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
      views.html.requests.actions(_)
    } else {
      views.html.requests.edit(_)
    }
    }

  override protected def listView = { implicit ctx =>
    (views.html.requests.list(_, _)).tupled }
}