package controllers.requests

import java.util.Date

import javax.inject.Inject
import models.BatchOrderRequest.{actionInfoFormat, batchRequestFormat}
import models.{NotificationType, Role, _}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.User.UserIdentity
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters.{EnumFormatter, JsonFormatter}
import org.incal.play.security.SecurityRole
import org.incal.play.security.SecurityUtil.restrictSubjectPresentAny
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.mailer.MailerClient
import play.api.mvc.{AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}
import services.request.{ActionGraph, ActionNotificationService, ActionPermissionService}

import scala.concurrent.Future


@Deprecated
class BatchOrderRequestsController @Inject()(
                                       requestsRepo: BatchOrderRequestRepo,

                                              userRepo: UserRepo,
                                              committeeRepo: ApprovalCommitteeRepo,
                                              val userManager: UserManager,
                                              val actionPermissionService: ActionPermissionService,
                                              val actionNotificationService: ActionNotificationService,
                                              mailerClient: MailerClient
                                            ) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with SubjectPresentRestrictedCrudController[BSONObjectID]
  with HasBasicFormCreateView[BatchOrderRequest]
  with HasBasicFormShowView[BatchOrderRequest, BSONObjectID]
  with HasBasicFormEditView[BatchOrderRequest, BSONObjectID]
  with HasListView[BatchOrderRequest]
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
   committeeIds.flatMap(_.userIds).toSeq :+ existingRequest.get.createdById.get
  }

  def requestAction(requestId: BSONObjectID, action: RequestAction.Value, description: String) = restrictSubjectPresentAny(deadbolt){

    implicit request => {
      for {
        existingRequest <- repo.get(requestId)
        allowedStateAction <- Future { getNextState(existingRequest.get.state, action) }
        user <- currentUser(request)
        userIdsMapping <- determineUserIdsPerRole(existingRequest.get, allowedStateAction)
        isUserAllowed <- Future { actionPermissionService.checkUserAllowed(user, allowedStateAction, userIdsMapping) }
        usersToNotify <- retrieveUsersToNotify(userIdsMapping)

        id <- {
          val batchRequestWithState = user match {
            case Some(currentUser) =>
              val dateOfUpdate = new Date()
              val newState = allowedStateAction.toState
              var actionInfo = buildActionInfo(dateOfUpdate, currentUser._id, existingRequest.get.state, newState, Some(description))
              var updatedHistory = buildHistory(existingRequest.get.history, dateOfUpdate, currentUser._id, actionInfo)
              usersToNotify.map(userRoleMapping => userRoleMapping._2.foreach( userToNotify =>
                addNotification(buildNotification(existingRequest, userToNotify, userRoleMapping._1 ,newState, dateOfUpdate, currentUser)
              )))
                existingRequest.get.copy(state = newState, createdById = existingRequest.get.createdById, history = updatedHistory)
            case None => throw new AdaException("No logged user found")
          }
          repo.update(batchRequestWithState)
        }
      } yield {
        println(id)
        actionNotificationService.sendNotifications()
        requestsListRedirect.flashing("success" -> "state of request updated with success to: ")
      }
    }.recover(handleExceptions("request action"))
  }

  def retrieveUsersToNotify(userIdsMapping : Map[Role.Value, Traversable[BSONObjectID]]): Future[ Map[Role.Value, Traversable[User]]] = {
   val userIds: Seq[BSONObjectID]  =  userIdsMapping.flatMap(_._2).toSeq

   for {
     users <- userRepo.find(Seq(UserIdentity.name #-> userIds.map(id => Some(id))))
   }
     yield{
       val usersMapping: Map[BSONObjectID, User] = users.toSeq.map(u=>(u._id.get,u)).toMap

       userIdsMapping.map{mapping => (mapping._1,
         mapping._2.map(userId => usersMapping.get(userId).get)
       )}
     }
   }

  def getIds(role: Role.Value,existingRequest: BatchOrderRequest): Future[Traversable[BSONObjectID]]= {
    role match {
      case Role.Committee => {
        committeeRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map {
          _.flatMap(_.userIds)
        }
      }
      case Role.Requester => Future {   Seq(existingRequest.createdById.get) }
      case Role.Owner => Future{Seq(BSONObjectID.parse("5cc2b4b0ea0100ec0159ab13").get) // admin user for now, trace of owner id to be clarified and implemented
    }
    }
  }

def determineUserIdsPerRole(existingRequest: BatchOrderRequest, action: Action):Future[Map[Role.Value, Traversable[BSONObjectID]]] = {
  val roles: Seq[Role.Value] = action.notified :+ action.solicited

  for{
    committeeIds <- {
      roles.find(r => r == Role.Committee) match {
        case Some(role) => committeeRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map {
          _.flatMap(_.userIds)
        }
        case _ => Future {
          Traversable()
        }
      }
    }
    ownerIds <- {
      roles.find(r => r == Role.Owner) match {
        case Some(role) => Future {
          Traversable(BSONObjectID.parse("5cfa58261601007302c7f8ed").get)  // hardcodede for now, trace of owner id to be clarified and implemented,) }
        }
        case _ => Future {Traversable()}
      }
    }
    requesterId <- {
      roles.find(r => r == Role.Requester) match {
        case Some(role) => Future {
          Traversable(existingRequest.createdById.get)
        }
        case _ => Future {Traversable()}
      }
    }
  }
    yield{
      Map (
        Role.Committee -> committeeIds,
        Role.Requester -> requesterId,
        Role.Owner -> ownerIds
        )
    }
}

  def getUserIfAllowed(request : play.api.mvc.Request[_])= {
    currentUser(request)
  }

  def getNextState(currentState: BatchRequestState.Value, action: RequestAction.Value) = {
      ActionGraph.apply.get(currentState).get.find(validAction=>validAction.action == action) match {
        case Some(allowedAction) =>  allowedAction
        case None => throw new AdaException("Action '"+ action +"' not allowed for current state '" +currentState+"'")
      }
   }

  def addNotification(notification: NotificationInfo)={
       actionNotificationService.addNotification(notification)
  }

  def buildNotification(existingRequest: Option[BatchOrderRequest], targetUser: User, role: Role.Value, newState: BatchRequestState.Value, dateOfUpdate: Date, updatedByUser: User)={
  NotificationInfo(NotificationType.Advice,existingRequest.get._id.get,existingRequest.get.timeCreated,existingRequest.get.createdById.get.toString(),targetUser.ldapDn,role,
    targetUser.email,existingRequest.get.state,newState,dateOfUpdate,updatedByUser.ldapDn)
  }

  override protected type ListViewData = (
    Page[(BatchOrderRequest, String)],
    Seq[FilterCondition]
  )

  override protected def getListViewData(page: Page[BatchOrderRequest], conditions: Seq[FilterCondition] ) = { request =>
    for {
      users <- getUsers(page.items.map( item=>item ))
    } yield {
      val itemsWithName = page.items.map( item => (item, users.get(item.createdById.get).get.ldapDn)  )
      ( Page(itemsWithName,page.page,page.offset,page.total,page.orderBy),conditions)
    }
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