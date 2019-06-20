package controllers.requests

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import models.BatchOrderRequest.{actionInfoFormat, batchRequestFormat}
import models.{NotificationType, Role, _}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.User.UserIdentity
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters.{EnumFormatter, JsonFormatter}
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.mvc.{Action, AnyContent, Call, _}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}
import services.request.{ActionGraph, _}
import play.api.mvc.Results._
import scala.concurrent.Future
import javax.inject.Inject
import org.ada.server.services.UserManager
import org.ada.web.models.security.DeadboltUser
import scala.concurrent.ExecutionContext.Implicits.global

@Deprecated
class BatchOrderRequestsController @Inject()(
                                       requestsRepo: BatchOrderRequestRepo,
                                              userRepo: UserRepo,
                                              committeeRepo: ApprovalCommitteeRepo,
                                              val userManager: UserManager,
                                              val actionPermissionService: ActionPermissionService,
                                              val actionNotificationService: ActionNotificationService,
                                              val requestFilter: RequestFilterProvider,
                                              roleService: RoleProviderService,
                                              val validatorService: ActionDescriptionValidatorService
                                            ) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with SubjectPresentRestrictedCrudController[BSONObjectID]
  with HasBasicFormCreateView[BatchOrderRequest]
  with HasBasicFormEditView[BatchOrderRequest, BSONObjectID]
  with HasBasicFormShowView[BatchOrderRequest, BSONObjectID]
  with HasListView[BatchOrderRequest]
  with AdaAuthConfig
{

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
      "date" -> ignored(new Date()),
      "history"->ignored(Seq[ActionInfo]())
    )(BatchOrderRequest.apply)(BatchOrderRequest.unapply))

  override protected val homeCall = routes.BatchOrderRequestsController.find()

  override def get(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminOrUserCustomAny(isRequestAllowed(id,true))(toAuthenticatedAction(super.get(id)))

  override def edit(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminAny(noCaching = true) {toAuthenticatedAction( super.edit(id))}

  override def update(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminAny(noCaching = true) (toAuthenticatedAction(super.update(id)))

  override def delete(id: BSONObjectID): Action[AnyContent] =
    restrictAdminAny(noCaching = true) {toAuthenticatedAction( super.delete(id) )}

  /*
  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] =
    restrictAny(findRequestsForUser(page, orderBy, filter))

  override def listAll(orderBy: String): Action[AnyContent] = {
    restrictAny(findRequestsForUser(orderBy = orderBy, filter = Seq[FilterCondition]() ))
  }


  def findRequestsForUser(page: Int = 0, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] = {
  //  super.listAll(orderBy)
    val requestIdFilters: Seq[FilterCondition] = RequestFilterProvider().getRequestFilter()

    super.find(page, orderBy, filter ++ requestIdFilters)
  }  */

  override def saveCall(
                         batchRequest: BatchOrderRequest)(
                         implicit request: AuthenticatedRequest[AnyContent]
                       ): Future[BSONObjectID] =
  {
    val date = new Date()
    actionNotificationService.cleanNotifications()
    for {
      user <- currentUser(request)
      batchRequestWithUser =
        user match {
          case Some(user) =>
            val newState = BatchRequestState.Created
            val actionInfo = buildActionInfo(date,user.ldapDn,newState,newState,None)
            val newHistory = buildHistory(Seq(), actionInfo)
            batchRequest.copy(timeCreated = date, createdById = user._id, state = newState, history = newHistory)
          case None => throw new AdaException("No logged user found")
        }
      id <- {
        repo.save(batchRequestWithUser)
      }
      notification = {
        implicit val getRequestUrl: String = routes.BatchOrderRequestsController.get(id).absoluteURL()
        addNotification(buildNotification(Some(batchRequestWithUser.copy(_id=Some(id))), user.get, Role.Requester , ActionGraph.createAction() , date, user.get, getRequestUrl))
      }
    } yield {
      actionNotificationService.sendNotifications()
      id
    }
  }

  def buildHistory(currentHistory: Seq[ActionInfo], actionInfo:ActionInfo):Seq[ActionInfo] ={
    currentHistory.isEmpty match {
    case false => {
      currentHistory :+ actionInfo
    }
    case true => List(actionInfo)
  }
  }

  def buildActionInfo(date: Date,userName: String,fromState:BatchRequestState.Value, toState: BatchRequestState.Value, description: Option[String]):ActionInfo={
    ActionInfo(date, userName,fromState,toState, description)
  }

  def getUserIds(committeeIds: Traversable[ApprovalCommittee], existingRequest: Option[BatchOrderRequest]): Seq[BSONObjectID] = {
   committeeIds.flatMap(_.userIds).toSeq :+ existingRequest.get.createdById.get
  }


  def isActionAllowed(
    requestId: BSONObjectID,
    action: RequestAction.Value
               )(
  deadboltUser: DeadboltUser,
                 request: AuthenticatedRequest[Any]
  ):Future[Boolean] = {
    for {
      existingRequestOption <- repo.get(requestId)
      allowedStateAction = { getNextState(existingRequestOption.get.state, action) }
      userIdsMapping <- determineUserIdsPerRole(existingRequestOption.get, getAllowedRolesByAction(allowedStateAction))
    } yield{
      actionPermissionService.checkUserAllowed(Some(deadboltUser.user), Set(allowedStateAction.allowed), userIdsMapping)
    }
   }


  def isRequestAllowed(
                       requestId: BSONObjectID,
                       readOnly: Boolean
                     )(
                       deadboltUser: DeadboltUser,
                       request: AuthenticatedRequest[Any]
                     ):Future[Boolean] = {
    for {
      existingRequestOption <- repo.get(requestId)
      userIdsMapping <- determineUserIdsPerRole(existingRequestOption.get, Role.values.toSeq)
    } yield{
       readOnly match{
       case false => {
          val validRoles = ActionGraph.apply.get(existingRequestOption.get.state).get.map(action => action.allowed).toSet
          actionPermissionService.checkUserAllowed(Some(deadboltUser.user), validRoles, userIdsMapping)
        }
        case true => {
           actionPermissionService.checkUserAllowed(Some(deadboltUser.user),Set(Role.Requester,Role.Owner,Role.Committee), userIdsMapping)
        }
      }
    }
  }


def getAllowedRolesByAction(action: models.Action)={
  action.notified :+ action.solicited
}

  def performAction(requestId: BSONObjectID, action: RequestAction.Value, description: String)= restrictAdminOrUserCustomAny(isActionAllowed(requestId, action))
  {
    implicit request => {
      implicit val getRequestUrl: String = routes.BatchOrderRequestsController.get(requestId).absoluteURL()
      actionNotificationService.cleanNotifications()

      for {
        existingRequest <- repo.get(requestId)
        user <- currentUser(request)
        allowedStateAction = { getNextState(existingRequest.get.state, action) }
        descriptionExists = { checkDescriptionExists(allowedStateAction, description) }
        userIdsMapping <- determineUserIdsPerRole(existingRequest.get, getAllowedRolesByAction(allowedStateAction))
        usersToNotify <- retrieveUsersToNotify(userIdsMapping)
        id <- {
          val batchRequestWithState = user match {
            case Some(currentUser) =>
              val dateOfUpdate = new Date()
              val newState = allowedStateAction.toState
              val actionInfo = buildActionInfo(dateOfUpdate, currentUser.ldapDn, existingRequest.get.state, newState, Some(description))
              val updatedHistory = buildHistory(existingRequest.get.history, actionInfo)
              usersToNotify.map(userRoleMapping => userRoleMapping._2.foreach( userToNotify =>
                addNotification(buildNotification(existingRequest, userToNotify, userRoleMapping._1 ,allowedStateAction, dateOfUpdate, currentUser, getRequestUrl)
              )))
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

  def checkDescriptionExists(action:models.Action, description: String)= {
    action.commentNeeded match {
      case true => {
        validatorService.validate(description) match {
          case false => throw new AdaException("Description not provided or not accepted '" + description + "'" + " for new state: " + action.toState)
          case _ =>
        }
      }
      case _ =>
    }
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

def determineUserIdsPerRole(existingRequest: BatchOrderRequest, allowedRoles: Seq[Role.Value]):Future[Map[Role.Value, Traversable[BSONObjectID]]] = {

  for{
    committeeIds <- {
      allowedRoles.find(r => r == Role.Committee) match {
        case Some(role) => committeeRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map {
          _.flatMap(_.userIds)
        }
        case _ => Future {
          Traversable()
        }
      }
    }
    ownerIds <- {
      allowedRoles.find(r => r == Role.Owner) match {
        case Some(role) => Future {
          Traversable(BSONObjectID.parse("5cfa58261601007302c7f8ed").get)  // hardcodede for now, trace of owner id to be clarified and implemented,) }
        }
        case _ => Future {Traversable()}
      }
    }
    requesterId <- {
      allowedRoles.find(r => r == Role.Requester) match {
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

  def getNextState(currentState: BatchRequestState.Value, action: RequestAction.Value) : models.Action = {
        ActionGraph.apply.get(currentState).get.find(validAction => validAction.action == action) match {
          case Some(allowedAction) => allowedAction
          case None => throw new AdaException("Action '" + action + "' not allowed for current state '" + currentState + "'")
        }
  }

  def addNotification(notification: NotificationInfo)={
       actionNotificationService.addNotification(notification)
  }

  def buildNotification(existingRequest: Option[BatchOrderRequest], targetUser: User, role: Role.Value, action: models.Action, dateOfUpdate: Date, updatedByUser: User, getRequestUrl: String)= {
    action.notified.find(r => r == role) match {
      case Some(role) => {
        NotificationInfo(NotificationType.Advice,existingRequest.get._id.get,existingRequest.get.timeCreated,existingRequest.get.createdById.get.toString(),targetUser.ldapDn,role,
          targetUser.email,action.fromState,action.toState,dateOfUpdate,updatedByUser.ldapDn, getRequestUrl)
      }
      case _ => {
        NotificationInfo(NotificationType.Solicitation,existingRequest.get._id.get,existingRequest.get.timeCreated,existingRequest.get.createdById.get.toString(),targetUser.ldapDn,role,
          targetUser.email,action.fromState,action.toState,dateOfUpdate,updatedByUser.ldapDn, getRequestUrl)
      }
    }
  }

  override protected type ListViewData = (
    Page[(BatchOrderRequest, String, Call)],
    Seq[FilterCondition]
  )

  override protected def getListViewData(page: Page[BatchOrderRequest], conditions: Seq[FilterCondition] ) = { request =>
    for {
      currentUser <- currentUser(request)
      isAdmin = roleService.isAdmin(currentUser)
      itemsForUser <- requestFilter.filterForCurrentUser(isAdmin, page, conditions, currentUser, getUsers)
    } yield {
     val itemsForUserWithCall = itemsForUser.map(item => (item._1,item._2, itemViewRouting(item._1, currentUser)))
     buildPageWithNames(itemsForUserWithCall,page, conditions)
    }
  }

def buildPageWithNames(itemsWithName: Traversable[(BatchOrderRequest, String, Call)], page :Page[BatchOrderRequest], conditions: Seq[FilterCondition])={
  ( Page(itemsWithName,page.page,page.offset,itemsWithName.size,page.orderBy),conditions)
}

  def getUsers(requests: Traversable[BatchOrderRequest])={
    val userIds =requests.map(_.createdById).flatten.map(Some(_)).toSeq

    userRepo.find(Seq(BatchOrderRequest.BatchRequestIdentity.name #-> userIds)).map { users =>
      users.map(c => (c._id.get, c)).toMap
    }
  }

  override protected def createView = { implicit ctx => views.html.requests.create(_) }

  override protected def showView = { implicit ctx =>
    views.html.requests.show(_)
    }

  override protected def editView = { implicit ctx =>
      views.html.requests.edit(_)
    }


  def action(id: BSONObjectID) = restrictAdminOrUserCustomAny(isRequestAllowed(id, false)) {

    implicit request => {
      for {
        item <- repo.get(id)
        viewData <- item.fold(
          throw new AdaException("request with id '" + id.stringify + "' not found")
        ) { entity =>
          getEditViewData(id, entity)(request).map(Some(_))
        }
      } yield
        item match {
          case None => NotFound(s"$entityName '${formatId(id)}' not found")
          case Some(_) =>
            val form : IdForm[BSONObjectID, BatchOrderRequest] = IdForm(viewData.get.id,viewData.get.form)

            render {
              case Accepts.Html() => Ok(views.html.requests.actions(form))
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
        }
    }.recover(handleEditExceptions(id))
  }

  def itemViewRouting(request: BatchOrderRequest, userFuture: Option[User]): Call = {
    val currentStatus = request.state
    val userRole = roleService.getRole(request._id.get, userFuture)

    userRole match {
      case Role.Administrator => controllers.requests.routes.BatchOrderRequestsController.edit(request._id.get)
      case _ => {
        val applicableActions = ActionGraph.apply.get(currentStatus).getOrElse(Traversable()).filter(a => a.allowed == userRole)

        applicableActions.size > 0 match {
          case true => controllers.requests.routes.BatchOrderRequestsController.action(request._id.get)
          case false => controllers.requests.routes.BatchOrderRequestsController.get(request._id.get)
        }
      }
    }
  }

  override protected def listView = { implicit ctx =>
    (views.html.requests.list(_,_)).tupled
  }
}