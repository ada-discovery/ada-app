package controllers.requests

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.BatchOrderRequest.batchRequestFormat
import models.{NotificationType, Role, _}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.models.Filter.FilterOrId
import org.ada.server.models.User.UserIdentity
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.models.security.DeadboltUser
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters.EnumFormatter
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Call, _}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}
import services.request.{ActionGraph, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Deprecated
class BatchOrderRequestsController @Inject()(
                                              requestsRepo: BatchOrderRequestRepo,
                                              userRepo: UserRepo,
                                              committeeRepo: ApprovalCommitteeRepo,
                                              dataSetSettingRepo: DataSetSettingRepo,
                                              val userManager: UserManager,
                                              val actionPermissionService: ActionPermissionService,
                                              val actionNotificationService: ActionNotificationService,
                                              roleService: RoleProviderService,
                                              userIdByRoleProvider: UserIdByRoleProviderImpl,
                                              val validatorService: ActionDescriptionValidatorService,
                                              val urlProvider: AbsoluteUrlProvider,
                                              criterionBuilder: ListCriterionBuilder,
                                              itemsProvider: DataSetItemsProvider,
                                              fieldNamesProvider: FieldNamesProvider
                                            ) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with SubjectPresentRestrictedCrudController[BSONObjectID]
  with HasBasicFormCreateView[BatchOrderRequest]
  with HasBasicFormEditView[BatchOrderRequest, BSONObjectID]
  with HasBasicFormShowView[BatchOrderRequest, BSONObjectID]
  with HasListView[BatchOrderRequest]
  with AdaAuthConfig {
   private implicit val idsFormatter = BSONObjectIDStringFormatter
   private implicit val requestStateFormatter = EnumFormatter(BatchRequestState)
   private val activeRequestsListRedirect = Redirect(routes.BatchOrderRequestsController.findActive())

  def selectItemsRedirect(requestId: BSONObjectID, dataSetId: String) = {
    Redirect(routes.BatchOrderRequestsController.select(Some(requestId),dataSetId))
  }

  override protected val homeCall = {
    routes.BatchOrderRequestsController.findActive()
  }

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "itemIds" -> seq(of[BSONObjectID]),
      "state" -> of[BatchRequestState.Value],
      "created by id" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date()),
      "history" -> ignored(Seq[ActionInfo]())
    )(BatchOrderRequest.apply)(BatchOrderRequest.unapply))

  override def get(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminOrUserCustomAny(isRequestAllowed(id, true))(toAuthenticatedAction(super.get(id)))

  override def edit(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminAny(noCaching = true) {
      toAuthenticatedAction(super.edit(id))
    }

  override def update(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.update(id)))

  override def delete(id: BSONObjectID): Action[AnyContent] =
    restrictAdminAny(noCaching = true) {
      toAuthenticatedAction(super.delete(id))
    }

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] =
    restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.find(page, orderBy, filter)))

  def select(requestId: Option[BSONObjectID], dataSetId: String, page: Int, orderBy: String, filter: Seq[FilterCondition], filterOrId: FilterOrId): Action[AnyContent] =
    restrictAdminOrUserCustomAny(isUserRequester(requestId.get))(toAuthenticatedAction(
      selectItems(requestId, dataSetId, page, orderBy,filter, filterOrId)
    ))

  def selectItems(requestIdOption: Option[BSONObjectID], dataSetId: String, page: Int, orderBy: String, filter: Seq[FilterCondition], filterOrId: FilterOrId): Action[AnyContent] = AuthAction
  {
    implicit request => {
    val requestId = requestIdOption.get

    for {
      items <- Future {1}
      fieldNames <- fieldNamesProvider.getFieldNames(dataSetId)
      tableWithFilter<-  itemsProvider.retrieveTableWithFilter(page, orderBy,filterOrId, fieldNames.toSeq)

      item <- repo.get(requestId)
      viewData <- item.fold(
        throw new AdaException("request with id '" + requestId.stringify + "' not found")
      ) { entity =>
        getEditViewData(requestId, entity)(request).map(Some(_))
      }
    } yield {
      item match {
        case None => NotFound(s"$entityName '${formatId(requestId)}' not found")
        case Some(_) =>
          render {
            case Accepts.Html() => Ok(views.html.layout.selectItems("",item.get.dataSetId,requestId, tableWithFilter,fieldNames))
            case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
          }
      }
    }
  }
    .recover(handleFindExceptions)
  }

  def findActive(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] = {
    restrictAny(findRequestsForUser(pageCreated, pageToApprove, orderBy, filter))
  }

  override def listAll(orderBy: String): Action[AnyContent] = {
   restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.listAll(orderBy)))
  }

  def getPageWithCriteria(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, criteria: Seq[Criterion[Any]], user: Option[User], approverCriterion: Seq[Criterion[Any]]) = {

    pageCreated.isDefined match {
      case true => (pageCreated.get, criteria ++ criterionBuilder.buildRequesterCriterion(user), PageType.Created)
      case false => pageToApprove.isDefined match {
        case true => (pageToApprove.get, criteria ++ approverCriterion, PageType.ToApprove)
        case false => (0, criteria ++ criterionBuilder.buildRequesterCriterion(user), PageType.Created)
      }
    }
  }

  def findRequestsForUser(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] = AuthAction { implicit request => {
    for {
      user <- currentUser(request)
      approverCriterion <- criterionBuilder.buildApproverCriterion(user)
      criteria <- toCriteria(filter)
      (page, userCriteria, pageType) = getPageWithCriteria(pageCreated, pageToApprove, orderBy, criteria, user, approverCriterion)
      (items, count) <- getFutureUserScopedItemsAndCount(Some(page), orderBy, userCriteria)
      viewData <- getUserScopedListViewData(Page(items, page, page * pageLimit, count, orderBy), filter)(request)
    } yield {
      implicit val req = request: Request[_]
      render {
        case Accepts.Html() => Ok((views.html.requests.listByCategory(viewData._1, pageType, filter)))
        case Accepts.Json() => Ok(toJson(items))
      }
    }
  }.recover(handleFindExceptions)
  }

  def getFutureUserScopedItemsAndCount(
                                        page: Option[Int],
                                        orderBy: String,
                                        criteria: Seq[Criterion[Any]],
                                        limit: Option[Int] = Some(pageLimit),
                                        projection: Seq[String] = listViewColumns.getOrElse(Nil)
                                      ): Future[(Traversable[BatchOrderRequest], Int)] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    for {
      itemsCount <- {
        val itemsFuture = repo.find(criteria, sort, projection, limit, skip)
        val countFuture = repo.count(criteria)

        for {items <- itemsFuture; count <- countFuture} yield
          (items, count)
      }
    } yield
      itemsCount
  }

  override def saveCall(
                         batchRequest: BatchOrderRequest)(
                         implicit request: AuthenticatedRequest[AnyContent]
                       ): Future[BSONObjectID] = {
    val date = new Date()
    actionNotificationService.cleanNotifications()
    for {
      user <- currentUser(request)
      batchRequestWithUser =
      user match {
        case Some(user) =>
          val newState = BatchRequestState.Created
          val actionInfo = buildActionInfo(date, user.ldapDn, newState, newState, None)
          val newHistory = buildHistory(Seq(), actionInfo)
          batchRequest.copy(timeCreated = date, createdById = user._id, state = newState, history = newHistory)
        case None => throw new AdaException("No logged user found")
      }
      id <- {
        repo.save(batchRequestWithUser)
      }
    } yield {
      addNotification(buildNotification(Some(batchRequestWithUser.copy(_id = Some(id))), user.get, Role.Requester, ActionGraph.createAction(), date, user.get, ""))
      actionNotificationService.sendNotifications()
      id
    }
  }

  override protected def save(redirect: Request[_] => Result) = AuthAction { implicit request =>
    formFromRequest.fold(
      formWithErrors => getFormCreateViewData(formWithErrors).map(viewData =>
        BadRequest(createViewWithContext(viewData))
      ),
      item =>
        saveCall(item).map { id =>
          render {
            case Accepts.Html() =>
              selectItemsRedirect(id, item.dataSetId).flashing("success" -> s"$entityName '${formatId(id)}' has been created")
            case Accepts.Json() => Created(Json.obj("message" -> s"$entityName successfully created", "id" -> formatId(id)))
          }
        }.recover(handleSaveExceptions)
    )
  }

  def buildHistory(currentHistory: Seq[ActionInfo], actionInfo: ActionInfo): Seq[ActionInfo] = {
    currentHistory.isEmpty match {
      case false => {
        currentHistory :+ actionInfo
      }
      case true => List(actionInfo)
    }
  }

  def buildActionInfo(date: Date, userName: String, fromState: BatchRequestState.Value, toState: BatchRequestState.Value, description: Option[String]): ActionInfo = {
    ActionInfo(date, userName, fromState, toState, description)
  }

  def getUserIds(committeeIds: Traversable[BatchRequestSetting], existingRequest: Option[BatchOrderRequest]): Seq[BSONObjectID] = {
    committeeIds.flatMap(_.userIds).toSeq :+ existingRequest.get.createdById.get
  }

  def isActionAllowed(
                       requestId: BSONObjectID,
                       action: RequestAction.Value,
                       role: Role.Value
                     )(
                       deadboltUser: DeadboltUser,
                       request: AuthenticatedRequest[Any]
                     ): Future[Boolean] = {
    for {
      existingRequestOption <- repo.get(requestId)
      allowedStateAction = {
        getNextState(existingRequestOption.get.state, action)
      }
      userIdsMapping <- userIdByRoleProvider.getIdByRole(existingRequestOption.get, None)
    } yield {
      actionPermissionService.checkUserAllowed(Some(deadboltUser.user), Some(role), Set(allowedStateAction.allowed), userIdsMapping)
    }
  }

  def isRequestAllowed(
                        requestId: BSONObjectID,
                        readOnly: Boolean,
                        role: Option[Role.Value] = None
                      )(
                        deadboltUser: DeadboltUser,
                        request: AuthenticatedRequest[Any]
                      ): Future[Boolean] = {
    for {
      existingRequestOption <- repo.get(requestId)
      userIdsMapping <- userIdByRoleProvider.getIdByRole(existingRequestOption.get, None)
    } yield {
   readOnly match {
       case false => {
          val validRoles = ActionGraph.apply.get(existingRequestOption.get.state).get.map(action => action.allowed).toSet
          actionPermissionService.checkUserAllowed(Some(deadboltUser.user), role, validRoles, userIdsMapping)
      }
        case true => {
          actionPermissionService.checkUserAllowed(Some(deadboltUser.user), role, Set(Role.Requester, Role.Owner, Role.Committee), userIdsMapping)
        }
      }
    }
  }

  def isUserRequester(
                        requestId: BSONObjectID
                      )(
                        deadboltUser: DeadboltUser,
                        request: AuthenticatedRequest[Any]
                      ): Future[Boolean] = {
    for {
      existingRequestOption <- repo.get(requestId)
    } yield {
      existingRequestOption.get.createdById.get == deadboltUser.id.get
    }
  }

  def addItemsToRequest(requestId: BSONObjectID, dataSetId: String, items: Seq[String]) =
    restrictAdminOrUserCustomAny(isUserRequester(requestId)) {
    implicit request => {

      for {
        existingRequest <- repo.get(requestId)
        user <- currentUser(request)
        id <- {
          val batchRequestWithState = user match {
            case Some(currentUser) =>
              existingRequest.get.copy(itemIds = items.map(id=> BSONObjectID.parse(id.replace("\"","")).get))
            case None => throw new AdaException("No logged user found")
          }
          repo.update(batchRequestWithState)
        }
      } yield {
        activeRequestsListRedirect.flashing("success" -> "items added successfully ")
      }
    }.recover(handleExceptions("request action"))
  }


  def performAction(requestId: BSONObjectID, action: RequestAction.Value, role: Role.Value, description: String) = restrictAdminOrUserCustomAny(isActionAllowed(requestId, action, role)) {
    implicit request => {
     actionNotificationService.cleanNotifications()

      for {
        existingRequest <- repo.get(requestId)
        user <- currentUser(request)
        allowedStateAction = {
          getNextState(existingRequest.get.state, action)
        }
        descriptionExists = {
          checkDescriptionExists(allowedStateAction, description)
        }
        userIdsMapping <- userIdByRoleProvider.getIdByRole(existingRequest.get, None)
        usersToNotify <- retrieveUsersToNotify(userIdsMapping)
        id <- {
          val batchRequestWithState = user match {
            case Some(currentUser) =>
              val dateOfUpdate = new Date()
              val newState = allowedStateAction.toState
              val actionInfo = buildActionInfo(dateOfUpdate, currentUser.ldapDn, existingRequest.get.state, newState, Some(description))
              val updatedHistory = buildHistory(existingRequest.get.history, actionInfo)
              usersToNotify.map(userRoleMapping => userRoleMapping._2.foreach(userToNotify =>
                addNotification(
                  buildNotification(existingRequest, userToNotify, userRoleMapping._1, allowedStateAction, dateOfUpdate, currentUser, description)
                )))
              existingRequest.get.copy(state = newState, createdById = existingRequest.get.createdById, history = updatedHistory)
            case None => throw new AdaException("No logged user found")
          }
          repo.update(batchRequestWithState)
        }
      } yield {
        id
        actionNotificationService.sendNotifications()
        activeRequestsListRedirect.flashing("success" -> "state of request updated with success to: ")
      }
    }.recover(handleExceptions("request action"))
  }

  def checkDescriptionExists(action: models.Action, description: String) = {
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

  def retrieveUsersToNotify(userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]): Future[Map[Role.Value, Traversable[User]]] = {
    val userIds: Seq[BSONObjectID] = userIdsMapping.flatMap(_._2).toSeq

    for {
      users <- userRepo.find(Seq(UserIdentity.name #-> userIds.map(id => Some(id))))
    }
      yield {
        val usersMapping: Map[BSONObjectID, User] = users.toSeq.map(u => (u._id.get, u)).toMap

        userIdsMapping.map { mapping =>
          (mapping._1,
            mapping._2.map(userId => usersMapping.get(userId).get)
          )
        }
      }
  }

  def getUserIfAllowed(request: play.api.mvc.Request[_]) = {
    currentUser(request)
  }

  def getNextState(currentState: BatchRequestState.Value, action: RequestAction.Value): models.Action = {
    ActionGraph.apply.get(currentState).get.find(validAction => validAction.action == action) match {
      case Some(allowedAction) => allowedAction
      case None => throw new AdaException("Action '" + action + "' not allowed for current state '" + currentState + "'")
    }
  }

  def addNotification(notification: Option[NotificationInfo]) = {
    actionNotificationService.addNotification(notification)
  }

  def buildNotification(existingRequest: Option[BatchOrderRequest], targetUser: User, role: Role.Value, action: models.Action, dateOfUpdate: Date, updatedByUser: User, description: String)(implicit request: RequestHeader) = {
    action.notified.find(r => r == role) match {
      case Some(role) => {
        Some(NotificationInfo(NotificationType.Advice, existingRequest.get._id.get, existingRequest.get.timeCreated, existingRequest.get.createdById.get.toString(), targetUser.ldapDn, role,
          targetUser.email, action.fromState, action.toState, dateOfUpdate, updatedByUser.ldapDn, urlProvider.getReadOnlyUrl(existingRequest.get._id.get), description))
      }
      case None => {
        action.solicited == role match {
          case true => {
            Some(NotificationInfo(NotificationType.Solicitation, existingRequest.get._id.get, existingRequest.get.timeCreated, existingRequest.get.createdById.get.toString(), targetUser.ldapDn, role,
              targetUser.email, action.fromState, action.toState, dateOfUpdate, updatedByUser.ldapDn, urlProvider.getActionUrl(existingRequest.get._id.get, role), description))
          }
          case _ => None
        }
      }
    }
  }

  override protected type ListViewData = (
    Page[(BatchOrderRequest, String, Call)],
      Seq[FilterCondition]
    )

  protected type UserScopedListViewData = (
    Page[(BatchOrderRequest, String, Call)],
      Seq[FilterCondition]
    )

  protected def getUserScopedListViewData(page: Page[BatchOrderRequest], conditions: Seq[FilterCondition]): AuthenticatedRequest[_] => Future[UserScopedListViewData] = {
    request => {

      for {
        currentUser <- currentUser(request)
        users <- getUsers(page.items)
        userRolesByRequest <- roleService.getRolesMapping(page.items, currentUser)
      } yield {
        val pageItemsWithCall = buildItems(page.items, users, currentUser, userRolesByRequest)
        (buildPageWithNames(pageItemsWithCall, page), conditions)
      }
    }
  }

  def buildItems(items: Traversable[BatchOrderRequest], users: Map[BSONObjectID, User], currentUser: Option[User], rolesByRequestId: Map[BSONObjectID, Traversable[Role.Value]]) = {
    items.map(item => (item, users.get(item.createdById.get).get.ldapDn, itemViewRouting(item, currentUser, rolesByRequestId.get(item._id.get).get)))
  }

  override protected def getListViewData(page: Page[BatchOrderRequest], conditions: Seq[FilterCondition]) = { request =>
    for {
      currentUser <- currentUser(request)
      isAdmin = roleService.isAdmin(currentUser)
      users <- getUsers(page.items)
      userRolesByRequest <- roleService.getRolesMapping(page.items, currentUser)
    } yield {
      val itemsForUserWithCall =
        buildItems(page.items, users, currentUser, userRolesByRequest)
      (buildPageWithNames(itemsForUserWithCall, page), conditions)
    }
  }

  def buildPageWithNames(itemsWithName: Traversable[(BatchOrderRequest, String, Call)], page: Page[BatchOrderRequest]) = {
    Page(itemsWithName, page.page, page.offset, page.total, page.orderBy)
  }

  def getUsers(requests: Traversable[BatchOrderRequest]) = {
    val userIds = requests.map(_.createdById).flatten.map(Some(_)).toSeq

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

  def action(id: BSONObjectID, role: Role.Value) = restrictAdminOrUserCustomAny(isRequestAllowed(id, false, Some(role))) {
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
            val form: IdForm[BSONObjectID, BatchOrderRequest] = IdForm(viewData.get.id, viewData.get.form)

            render {
              case Accepts.Html() => Ok(views.html.requests.actions(form, role))
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
        }
    }.recover(handleEditExceptions(id))
  }

  def itemViewRouting(request: BatchOrderRequest, user: Option[User], roles: Traversable[Role.Value]): Call = {

    val userRolesPrioritized = PrioritizedRoles.roles.toSeq.sortBy(_._1).filter(p => roles.toSeq.contains(p._2))
    val applicableActions = ActionGraph.apply.get(request.state).getOrElse(Traversable())
    val allowedRoles = applicableActions.map(a => a.allowed).toSet
    val headRole = userRolesPrioritized.toStream.map(p => p._2).find(p => allowedRoles.contains(p)).headOption

    headRole match {
      case Some(role) => {
        role == Role.Administrator match {
          case true => controllers.requests.routes.BatchOrderRequestsController.edit(request._id.get)
          case false => controllers.requests.routes.BatchOrderRequestsController.action(request._id.get, role)
        }
      }
      case None => controllers.requests.routes.BatchOrderRequestsController.get(request._id.get)
    }
  }

  override protected def listView = { implicit ctx =>
    (views.html.requests.list(_, _)).tupled
  }
}