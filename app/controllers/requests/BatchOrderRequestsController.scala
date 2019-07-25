package controllers.requests

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.BatchOrderRequest.batchRequestFormat
import models.{NotificationType, Role, _}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.models.Filter.FilterOrId
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.FilterConditionExtraFormats.coreFilterConditionFormat
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.{DataSetWebContext, TableViewData}
import org.ada.web.models.security.DeadboltUser
import org.ada.web.security.AdaAuthConfig
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
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
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, RequestSettingRepo}
import services.UserProviderService
import services.request.{ActionGraph, _}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Deprecated
class BatchOrderRequestsController @Inject()(
                                              requestsRepo: BatchOrderRequestRepo,
                                              committeeRepo: RequestSettingRepo,
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
                                              fieldNamesProvider: FieldNamesProvider,
                                              userProvider: UserProviderService
                                            ) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with SubjectPresentRestrictedCrudController[BSONObjectID]
  with HasBasicFormCreateView[BatchOrderRequest]
  with HasEditView[BatchOrderRequest, BSONObjectID]
  with HasShowView[BatchOrderRequest, BSONObjectID]
  with HasListView[BatchOrderRequest]
  with AdaAuthConfig {
   private implicit val idsFormatter = BSONObjectIDStringFormatter
   private implicit val requestStateFormatter = EnumFormatter(BatchRequestState)
   private val activeRequestsListRedirect = Redirect(routes.BatchOrderRequestsController.findActive())

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

//  def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  override def get(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
    restrictAdminOrUserCustomAny(isRequestAllowed(id,None, None ,true))(toAuthenticatedAction(super.get(id)))

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

  def findItems(dataSetId: String, page: Int, orderBy: String, filterOrId: FilterOrId, filterOrIds: Seq[FilterOrId]) =  restrictSubjectPresentAny(noCaching = true) {
    implicit request => {
      for {
        fieldNames <- fieldNamesProvider.getFieldNames(dataSetId)
        (tableViewData, newFilter) <- itemsProvider.retrieveTableWithFilterForSelection(page, dataSetId, orderBy, filterOrIds, Some(filterOrId), fieldNames.toSeq, None)
      } yield {
        val tableJson = views.html.requests.itemsPaginatedTable(dataSetId,tableViewData, tableViewData.page, tableViewData.filter, tableViewData.tableFields, isAjaxRefresh = true)
        val conditionPanel = views.html.filter.conditionPanel(Some(newFilter))
        val filterModel = Json.toJson( tableViewData.filter.get.conditions)

        request.method == "POST" match {
          case false => {
            Ok(Json.obj(
              "conditionPanel" -> conditionPanel.toString(),
              "filterModel" -> filterModel,
              "table" -> tableJson.toString()
            ))
          }
          case true => {
            Ok(tableJson)
          }
        }
      }
    }
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
      request.method == "POST" match {
       case false => {
         Ok((views.html.requests.listByCategory(viewData._1, pageType, filter)))
       }
       case true => {
         pageToApprove match {
           case Some(page) => Ok((views.html.requests.requestTable(viewData._1, filter, (p, o) => controllers.requests.routes.BatchOrderRequestsController.findActive(None, Some(p), o, filter), isAjaxRefresh = true)))
           case None => Ok((views.html.requests.requestTable(viewData._1, filter, (p, o) => controllers.requests.routes.BatchOrderRequestsController.findActive(Some(p), None, o, filter), isAjaxRefresh = true)))
         }
       }
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
                                      ): Future[(Traversable[(BatchOrderRequest, String)], Int)] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    for {
      (items, count) <- {
        val itemsFuture = repo.find(criteria, sort, projection, limit, skip)
        val countFuture = repo.count(criteria)


        for {items <- itemsFuture; count <- countFuture} yield
         (items, count)
      }
      users <- userProvider.getUsersByIds(items.map(_.createdById))
    } yield {
      val itemsWithName = items.map( i => (i, users.get(i.createdById.get).get.ldapDn))
      orderBy match  {
        case "createdby" => (itemsWithName.toSeq.sortWith(_._2 < _._2), count)
        case "-createdby" => (itemsWithName.toSeq.sortWith(_._2 > _._2), count)
        case _ => (itemsWithName, count)
      }
    }
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
      addNotification(buildNotification(Some(batchRequestWithUser.copy(_id = Some(id))), user.get, Role.Requester, ActionGraph.createAction(), date,user.get, user.get, None, None))
      actionNotificationService.sendNotifications()
      id
    }
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

  def isRequestAllowed(
                        requestId: BSONObjectID,
                        action: Option[RequestAction.Value] = None,
                        assumedRole: Option[Role.Value] = None,
                        readOnly: Boolean = false
                      )(
                        deadboltUser: DeadboltUser,
                        request: AuthenticatedRequest[Any]
                      ):  Future[Boolean] = {
    for {
      existingRequestOption <- repo.get(requestId)
      allowedRoles = determineValidRoles(action, assumedRole, readOnly, existingRequestOption.get)
      userIdsMapping <- userIdByRoleProvider.getIdByRole(existingRequestOption.get, None)
    } yield {
      actionPermissionService.checkUserHasRoles(Some(deadboltUser.user), assumedRole, allowedRoles, userIdsMapping)
    }
  }

  def determineValidRoles(action: Option[RequestAction.Value],role: Option[Role.Value], readOnly: Boolean, existingRequest: BatchOrderRequest): Set[Role.Value] = {
    readOnly match {
      case false => {
        action match {
          case Some(action) =>
            role match {
              case Some(role) => {
                getNextState(existingRequest.state, action).allowed == role match {
                  case true => Set(role)
                  case false => Set()
                }
              }
                case None => throw new AdaException("No role existing with action request "+ action)
              }
          case None => {
            ActionGraph.apply.get(existingRequest.state).get.map(a => a.allowed).toSet
          }
        }
      }
      case true => Set(Role.Requester, Role.Owner, Role.Committee)
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

  def performAction(requestId: BSONObjectID, action: RequestAction.Value, role: Role.Value, description: Option[String]) = restrictAdminOrUserCustomAny(isRequestAllowed(requestId, Some(action), Some(role), false)) {
    implicit request => {
     actionNotificationService.cleanNotifications()

      for {
        existingRequest <- repo.get(requestId)
        itemsExist =  checkItemsExist(existingRequest.get)
        user <- currentUser(request)
        allowedStateAction = {
          getNextState(existingRequest.get.state, action)
        }
        descriptionExists = {
          checkDescriptionExists(allowedStateAction, description)
        }
        userIdsMapping <- userIdByRoleProvider.getIdByRole(existingRequest.get, None)
        usersToNotify <- retrieveUsersToNotify(userIdsMapping)
        fieldNames <- fieldNamesProvider.getFieldNames(existingRequest.get.dataSetId)
        items <- itemsProvider.getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId, fieldNames)
        id <- {
          val batchRequestWithState = user match {
            case Some(currentUser) =>
              val dateOfUpdate = new Date()
              val newState = allowedStateAction.toState
              val actionInfo = buildActionInfo(dateOfUpdate, currentUser.ldapDn, existingRequest.get.state, newState, description)
              val updatedHistory = buildHistory(existingRequest.get.history, actionInfo)
              usersToNotify.map(userRoleMapping => userRoleMapping._2.foreach(userToNotify =>
                addNotification(
                  buildNotification(existingRequest, userToNotify, userRoleMapping._1, allowedStateAction, dateOfUpdate, usersToNotify.get(Role.Requester).get.toSeq(0), currentUser, description, items.map(i=>i._1))
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

  def checkItemsExist(request: BatchOrderRequest)={
    request.itemIds.size > 0 match
    {
      case false => throw new AdaException("No item in this request, please select items before submitting")
      case _ =>
    }
  }

  def checkDescriptionExists(action: models.Action, description: Option[String]) = {
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
    val userIds: Seq[Option[BSONObjectID]] = userIdsMapping.flatMap(_._2.map(Some(_))).toSeq

    for {
      users <- userProvider.getUsersByIds(userIds)
    }
      yield {
        userIdsMapping.map { mapping =>
          (mapping._1,
            mapping._2.map(userId => users.get(userId).get)
          )
        }
      }
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

  def buildNotification(existingRequest: Option[BatchOrderRequest], targetUser: User, role: Role.Value, action: models.Action, dateOfUpdate: Date,createdByUser: User, updatedByUser: User, description: Option[String], itemsOption: Option[TableViewData])(implicit request: RequestHeader) = {
    action.notified.find(r => r == role) match {
      case Some(role) => {
        Some(NotificationInfo(NotificationType.Advice, existingRequest.get.dataSetId, existingRequest.get.timeCreated, createdByUser.ldapDn, targetUser.ldapDn, role,
          targetUser.email, action.fromState, action.toState, dateOfUpdate, updatedByUser.ldapDn, urlProvider.getReadOnlyUrl(existingRequest.get._id.get), description, itemsOption))
      }
      case None => {
        action.solicited == role match {
          case true => {
            Some(NotificationInfo(NotificationType.Solicitation, existingRequest.get.dataSetId, existingRequest.get.timeCreated, createdByUser.ldapDn, targetUser.ldapDn, role,
              targetUser.email, action.fromState, action.toState, dateOfUpdate, updatedByUser.ldapDn, urlProvider.getActionUrl(existingRequest.get._id.get, role), description, itemsOption))
          }
          case _ => None
        }
      }
    }
  }

  override protected type EditViewData = ViewData

  protected type ViewData = (
    IdForm[BSONObjectID, BatchOrderRequest],
      Option[TableViewData]
    )

  override protected type ListViewData = (
    Page[(BatchOrderRequest, String, Call)],
      Seq[FilterCondition]
    )

  override protected type ShowViewData = ViewData

  protected type UserScopedListViewData = (
    Page[(BatchOrderRequest, String, Call)],
      Seq[FilterCondition]
    )

  override protected def getFormEditViewData(requestId: BSONObjectID, form: Form[BatchOrderRequest]): AuthenticatedRequest[_] => Future[EditViewData] = {
    implicit request => {
      getRequestWithItems(requestId, form)
    }
  }

  override protected def getFormShowViewData(requestId: BSONObjectID, form: Form[BatchOrderRequest]): AuthenticatedRequest[_] => Future[ViewData]  =
    {
      implicit request => {
       getRequestWithItems(requestId, form)
      }
    }

  def getRequestWithItems(requestId: BSONObjectID, form: Form[BatchOrderRequest])(implicit request: AuthenticatedRequest[_]): Future[(IdForm[BSONObjectID, BatchOrderRequest], Option[TableViewData])] = {

      for {
        existingRequest <- repo.get(requestId)
        fieldNames <- fieldNamesProvider.getFieldNames(existingRequest.get.dataSetId)
        items <- itemsProvider.getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId, fieldNames.toSeq)
      } yield {
        (IdForm(requestId, form), items.map(i=>i._1))
      }
    }

  protected def getUserScopedListViewData(page: Page[(BatchOrderRequest, String)], conditions: Seq[FilterCondition]): AuthenticatedRequest[_] => Future[UserScopedListViewData] = {
    request => {

      for {
        currentUser <- currentUser(request)
        userRolesByRequest <- roleService.getRolesMapping(page.items.map(_._1), currentUser)
      } yield {
        val pageItemsWithCall = buildItemsWithCall(page.items, currentUser, userRolesByRequest)
        (Page(pageItemsWithCall, page.page, page.offset, page.total, page.orderBy), conditions)
      }
    }
  }

  def buildItemsWithCall(items: Traversable[(BatchOrderRequest, String)], currentUser: Option[User], rolesByRequestId: Map[BSONObjectID, Traversable[Role.Value]]) = {
    items.map(item => (item._1, item._2, itemViewRouting(item._1, currentUser, rolesByRequestId.get(item._1._id.get).get)))
  }

  def buildItemsWithUserAndCall(items: Traversable[BatchOrderRequest], users: Map[BSONObjectID, User], currentUser: Option[User], rolesByRequestId: Map[BSONObjectID, Traversable[Role.Value]]) = {
    items.map(item => (item, users.get(item.createdById.get).get.ldapDn, itemViewRouting(item, currentUser, rolesByRequestId.get(item._id.get).get)))
  }

  override protected def getListViewData(page: Page[BatchOrderRequest], conditions: Seq[FilterCondition]) = { request =>
    for {
      currentUser <- currentUser(request)
      isAdmin = roleService.isAdmin(currentUser)
      users <- userProvider.getUsersByIds(page.items.map(_.createdById))
      userRolesByRequest <- roleService.getRolesMapping(page.items, currentUser)
    } yield {
      val itemsForUserWithCall =
        buildItemsWithUserAndCall(page.items, users, currentUser, userRolesByRequest)
      (Page(itemsForUserWithCall, page.page, page.offset, page.total, page.orderBy), conditions)
    }
  }

  override protected def createView = { implicit ctx => views.html.requests.create(_) }

  override protected def showView = { implicit ctx =>
    (views.html.requests.show(_, _)).tupled
  }

  override protected def editView = { implicit ctx =>
    (views.html.requests.edit(_, _)).tupled
  }

  def action(id: BSONObjectID, role: Role.Value) = restrictAdminOrUserCustomAny(isRequestAllowed(id, None, Some(role),false)) {
    implicit request => {
      for {
        existingRequest <- repo.get(id)
        editViewData <- existingRequest.fold(
          throw new AdaException("request with id '" + id.stringify + "' not found")
        ) { entity =>
          getEditViewData(id, entity)(request).map(Some(_))
        }
        fieldNames <- fieldNamesProvider.getFieldNames(existingRequest.get.dataSetId)
        items <- itemsProvider.getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId,fieldNames.toSeq)
      } yield
        existingRequest match {
          case None => NotFound(s"$entityName '${formatId(id)}' not found")
          case Some(_) =>
            val form: IdForm[BSONObjectID, BatchOrderRequest] = IdForm(editViewData.get._1.id, editViewData.get._1.form)

            render {
              case Accepts.Html() => Ok(views.html.requests.actions(form, role, items))
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
        }
    }.recover(handleEditExceptions(id))
  }

  def itemViewRouting(request: BatchOrderRequest, user: Option[User], roles: Traversable[Role.Value]): Call = {
    val userRolesPrioritized = PrioritizedRoles.roles.toSeq.sortBy(_._1).filter(p => roles.toSeq.contains(p._2))
    val applicableActions = ActionGraph.apply.get(request.state).getOrElse(Traversable())
    val allowedRoles = applicableActions.map(a => a.allowed).toSet
    val headRole = userRolesPrioritized.toStream.map(p => p._2).find(p => allowedRoles.contains(p) || p == Role.Administrator).headOption

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