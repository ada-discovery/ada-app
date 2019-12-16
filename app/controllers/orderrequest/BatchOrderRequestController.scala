package controllers.orderrequest

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.orderrequest.routes
import javax.inject.Inject
import models.BatchOrderRequest.batchRequestFormat
import models.{NotificationInfo, NotificationType, Role, _}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.models.User.UserIdentity
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, Filter, User}
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.DataSetWebContext
import org.ada.web.models.security.DeadboltUser
import org.ada.web.services.DataSpaceService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters.EnumFormatter
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.json.JsObject
import play.api.mvc.{Action, AnyContent, Call, RequestHeader}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, BatchOrderRequestSettingRepo}
import services.request.{ActionNotificationService, BatchOrderService}
import services.request.ActionGraph
import views.html.dataset.actionTable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Deprecated
class BatchOrderRequestController @Inject()(
    requestsRepo: BatchOrderRequestRepo,
    requestSettingRepo: BatchOrderRequestSettingRepo,
    dataSetSettingRepo: DataSetSettingRepo,
    dsaf: DataSetAccessorFactory,
    val actionNotificationService: ActionNotificationService,
    batchOrderService: BatchOrderService,
    dataSpaceService: DataSpaceService,
    userRepo: UserRepo
) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
    with SubjectPresentRestrictedCrudController[BSONObjectID]
    with HasBasicFormCreateView[BatchOrderRequest]
    with HasEditView[BatchOrderRequest, BSONObjectID]
    with HasListView[BatchOrderRequest]
{
  private implicit val idsFormatter = BSONObjectIDStringFormatter
  private implicit val requestStateFormatter = EnumFormatter(BatchRequestState)

  override protected type EditViewData = (
    IdForm[BSONObjectID, BatchOrderRequest],
    Traversable[JsObject],
    Traversable[String]
  )

  override protected type ListViewData = (
    Page[(BatchOrderRequest, String, Call)],
    Seq[FilterCondition]
  )

  override protected type ShowViewData = (
    BatchOrderRequest,
    Traversable[JsObject],
    Traversable[String]
  )

  protected type UserScopedListViewData = (
    Page[(BatchOrderRequest, String, Call)],
    Seq[FilterCondition]
  )

  override protected val homeCall = routes.BatchOrderRequestController.findActive()

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "itemIds" -> seq(of[BSONObjectID]),
      "state" -> of[BatchRequestState.Value],
      "created by id" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date()),
      "history" -> ignored(Seq[ActionInfo]())
    )(BatchOrderRequest.apply)(BatchOrderRequest.unapply)
  )

  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  private val activeRequestsListRedirect = Redirect(routes.BatchOrderRequestController.findActive())
  private val selectedIdsForm = Form(single("selectedIds" -> seq(of[BSONObjectID])))

  def createNew(dataSet: String) = restrictAdminOrPermissionAny(s"DS:$dataSet:createRequest", noCaching = true) {
    implicit request =>
      dsaf(dataSet).map { dsa =>
        for {
          // get the batch-order setting for a given data set
          setting <- requestSettingRepo.find(Seq("dataSetId" #== dataSet)).map(_.headOption)

          dataViewOption <- dsa.dataViewRepo.get(setting.get.viewId)

          // handy things for a view: data set name, data space tree, and data set setting
          (dataSetName, dataSpaceTree, dataSetSetting) <- getDataSetNameTreeAndSetting(dsa)
        } yield
          setting match {
            case None =>
              NotFound(s"Batch-order setting for the data set '${dataSet}' not found.")
            case Some(setting) =>
              implicit val context = dataSetWebContext(dataSet)
              dataViewOption.map { dataView =>
                Ok(
                  actionTable(
                    dataView.tableColumnNames,
                    routes.BatchOrderRequestController.saveNew(dataSet),
                    "Request Items",
                    dataSetName + " Batch Order Request",
                    Filter(),
                    dataSetSetting,
                    dataSpaceTree
                  )
                )
              }.getOrElse(
                NotFound(s"Data view '${setting.viewId}' not found.")
              )
            }
          }.getOrElse(
            Future(NotFound(s"Data set '${dataSet}' doesn't exist."))
          )
  }

  private def getDataSetNameTreeAndSetting(
    dsa: DataSetAccessor)(
    implicit request: AuthenticatedRequest[_]
  ): Future[(String, Traversable[DataSpaceMetaInfo], DataSetSetting)] = {
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser
    val settingFuture = dsa.setting

    for {
        // get the data set name
        dataSetName <- dataSetNameFuture

        // get the data space tree
        dataSpaceTree <- treeFuture

        // get the data set setting
        setting <- settingFuture
    } yield
        (dataSetName, dataSpaceTree, setting)
  }

  def saveNew(dataSet: String) = restrictAdminOrPermissionAny(s"DS:$dataSet:createRequest", true) {
    implicit request =>
        selectedIdsForm.bindFromRequest.fold(
            _ =>  Future(BadRequest(s"Cannot parse selected ids.")),
            selectedIds =>
                if (selectedIds.size == 0) {
                    Future(BadRequest("No item added to the request."))
                } else {
                    for {
                        // get a currently-logged user
                        user <- currentUser()

                        // create a new batch-order request and save
                        id <- repo.save(BatchOrderRequest(
                            dataSetId = dataSet,
                            itemIds = selectedIds,
                            state = BatchRequestState.Created,
                            createdById = user.flatMap(_.user._id)
                        ))
                    } yield {
                        val date = new Date()
                        val createAction = ActionGraph.createAction()

                        val notification = NotificationInfo(
                            creationDate = date,
                            dataSetId = dataSet,
                            userRole = Role.Requester,
                            fromState = createAction.fromState,
                            toState = createAction.toState,
                            possibleActions =  ActionGraph.apply.get(createAction.toState).get.map(_.action),
                            createdByUser = user.get.user.ldapDn,
                            targetUser = user.get.user.ldapDn,
                            description = None,
                            targetUserEmail = user.get.user.email,
                            updateDate = date,
                            getRequestUrl = getActionUrl(id, Role.Requester),
                            notificationType = NotificationType.Solicitation,
                            updatedByUser = user.get.user.ldapDn,
                            items = None
                        )

                        actionNotificationService.sendNotifications(Traversable(notification))
                        Redirect(homeCall).flashing("success" -> s"New batch-order request '${id.stringify}' has been created.")
                    }
                }
        )
  }

  private def getActionUrl(requestId: BSONObjectID, role: Role.Value)(implicit request: RequestHeader) =
    routes.BatchOrderRequestController.action(requestId, role).absoluteURL()

  override def get(id: BSONObjectID) =
    restrictAdminOrUserCustomAny(isRequestAllowed(id, None, None, true))(toAuthenticatedAction(super.get(id)))

  override def edit(id: BSONObjectID) = restrictAdminAny(noCaching = true) {
    toAuthenticatedAction(super.edit(id))
  }

  override def update(id: BSONObjectID) = restrictAdminAny(noCaching = true)(
    toAuthenticatedAction(super.update(id))
  )

  override def delete(id: BSONObjectID) = restrictAdminAny(noCaching = true) {
    toAuthenticatedAction(super.delete(id))
  }

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) =
    restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.find(page, orderBy, filter)))

  def findActiveWithFilter(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] = {
    restrictAny(findRequestsForUserWithFilter(pageCreated, pageToApprove, orderBy, filter))
  }

  private def findRequestsForUserWithFilter(
    pageCreated: Option[Int] = None,
    pageToApprove: Option[Int] = None,
    orderBy: String,
    filter: Seq[FilterCondition]
  ) = AuthAction { implicit request => {
        for {
            user <- currentUser()
            approverCriterion <- buildApproverCriterion(user)
            criteria <- toCriteria(filter)
            (page, userCriteria, pageType, backgroundListCriteria) = getPageWithCriteria(pageCreated, pageToApprove, orderBy, criteria, user, approverCriterion)
            (items, count, backgroundListCount) <- getFutureUserScopedItemsAndCounts(Some(page), orderBy, userCriteria, backgroundListCriteria)
            viewData <- getUserScopedListViewData(Page(items, page, page * pageLimit, count, orderBy), filter)(request)
        } yield {
            Ok((views.html.requests.listByCategory(viewData._1, pageType, filter, backgroundListCount)))
        }
    }.recover(handleFindExceptions)
    }

    private def getPageWithCriteria(
        pageCreated: Option[Int] = None,
        pageToApprove: Option[Int] = None,
        orderBy: String,
        criteria: Seq[Criterion[Any]],
        user: Option[USER],
        approverCriterion: Seq[Criterion[Any]]
    ) = {
        val requesterCreiteria = criteria ++ buildRequesterCriterion(user)
        val approverCriteria = criteria ++ approverCriterion

        if (pageCreated.isDefined) {
            (pageCreated.get, requesterCreiteria, PageType.Created, approverCriteria)
        } else if (pageToApprove.isDefined) {
            (pageToApprove.get, approverCriteria, PageType.ToApprove, requesterCreiteria)
        } else {
            (0, requesterCreiteria, PageType.Created, approverCriteria)
        }
    }

    private def buildRequesterCriterion(user: Option[USER]) = {
        Seq("createdById" #== user.get.user._id)
    }

    private def buildApproverCriterion(user: Option[USER]) = {
        for {
            committees <- requestSettingRepo.find()
            dataSetSettings <- dataSetSettingRepo.find(Seq("ownerId" #== user.get.user._id))
        } yield {
            val userCommittee = committees.filter(c => c.committeeUserIds.contains(user.get.user._id.get))
            val committeeDataSetIds = userCommittee.map(c => c.dataSetId).toSeq
            val ownedDataSetIds = dataSetSettings.map(s => s.dataSetId).toSeq
            val dataSetIds = ownedDataSetIds ++ committeeDataSetIds
            Seq("dataSetId" #-> dataSetIds, "state" #!= BatchRequestState.Created.toString)
        }
    }

    private def getFutureUserScopedItemsAndCounts(
        page: Option[Int],
        orderBy: String,
        criteria: Seq[Criterion[Any]],
        backgroundPageCriteria: Seq[Criterion[Any]],
        limit: Option[Int] = Some(pageLimit),
        projection: Seq[String] = listViewColumns.getOrElse(Nil)
    ): Future[(Traversable[(BatchOrderRequest, String)], Int, Int)] = {
        val sort = toSort(orderBy)
        val skip = page.zip(limit).headOption.map { case (page, limit) =>
            page * limit
        }

        for {
            items <- repo.find(criteria, sort, projection, limit, skip)
            count <- repo.count(criteria)
            backgroundCount <- repo.count(backgroundPageCriteria)
            users <- getUsersByIds(items.map(_.createdById))
        } yield {
            val itemsWithName = items.map(i => (i, users.get(i.createdById.get).get.ldapDn))
            (itemsWithName, count, backgroundCount)
        }
    }

    protected def getUserScopedListViewData(
        page: Page[(BatchOrderRequest, String)],
        conditions: Seq[FilterCondition]
    ): AuthenticatedRequest[_] => Future[UserScopedListViewData] = {
        request => {
            for {
                currentUser <- currentUser()(request)
                userRolesByRequest <- batchOrderService.getUserRolesByRequest(page.items.map(_._1), currentUser)
            } yield {
                val pageItemsWithCall = buildItemsWithCall(page.items, currentUser, userRolesByRequest)
                (Page(pageItemsWithCall, page.page, page.offset, page.total, page.orderBy), conditions)
            }
        }
    }

    private def buildItemsWithCall(
        items: Traversable[(BatchOrderRequest, String)],
        currentUser: Option[USER],
        rolesByRequestId: Map[BSONObjectID, Traversable[Role.Value]]
    ) = {
        items.map(item => (item._1, item._2, itemViewRouting(item._1, currentUser, rolesByRequestId.get(item._1._id.get).get)))
    }

    private def itemViewRouting(request: BatchOrderRequest, user: Option[USER], roles: Traversable[Role.Value]): Call = {
        val userRolesPrioritized = PrioritizedRoles.apply.toSeq.sortBy(_._1).filter(p => roles.toSeq.contains(p._2))
        val applicableActions = ActionGraph.apply.get(request.state).getOrElse(Traversable())
        val allowedRoles = applicableActions.map(a => a.allowed).toSet
        val headRole = userRolesPrioritized.toStream.map(p => p._2).find(p => allowedRoles.contains(p) || p == Role.Administrator).headOption

        headRole match {
            case Some(role) => {
                if (role == Role.Administrator) {
                    controllers.orderrequest.routes.BatchOrderRequestController.edit(request._id.get)
                } else {
                    controllers.orderrequest.routes.BatchOrderRequestController.action(request._id.get, role)
                }
            }
            case None => controllers.orderrequest.routes.BatchOrderRequestController.get(request._id.get)
        }
    }

    def findActive(
        pageCreated: Option[Int] = None,
        pageToApprove: Option[Int] = None,
        orderBy: String,
        filter: Seq[FilterCondition]
    ): Action[AnyContent] = {
        restrictAny(findRequestsForUser(pageCreated, pageToApprove, orderBy, filter))
    }

    private def findRequestsForUser(
        pageCreated: Option[Int] = None,
        pageToApprove: Option[Int] = None,
        orderBy: String,
        filter: Seq[FilterCondition]
    ): Action[AnyContent] = AuthAction { implicit request => {
        for {
            user <- currentUser()
            approverCriterion <- buildApproverCriterion(user)
            criteria <- toCriteria(filter)
            (page, userCriteria, pageType, backgroundListCriteria) = getPageWithCriteria(pageCreated, pageToApprove, orderBy, criteria, user, approverCriterion)
            (items, count, backgroundListCount) <- getFutureUserScopedItemsAndCounts(Some(page), orderBy, userCriteria, backgroundListCriteria)
            viewData <- getUserScopedListViewData(Page(items, page, page * pageLimit, count, orderBy), filter)(request)
        } yield {
            pageToApprove match {
                case Some(page) => Ok((views.html.requests.requestTable(viewData._1, filter, (p, o) => controllers.orderrequest.routes.BatchOrderRequestController.findActive(None, Some(p), o, filter), isAjaxRefresh = true)))
                case None => Ok((views.html.requests.requestTable(viewData._1, filter, (p, o) => controllers.orderrequest.routes.BatchOrderRequestController.findActive(Some(p), None, o, filter), isAjaxRefresh = true)))
            }
        }
    }.recover(handleFindExceptions)
    }

    override def listAll(orderBy: String): Action[AnyContent] = {
        restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.listAll(orderBy)))
    }

    override def saveCall(
        batchRequest: BatchOrderRequest
    )(
        implicit request: AuthenticatedRequest[AnyContent]
    ): Future[BSONObjectID] = {
        val date = new Date()
        for {
            user <- currentUser()
            batchRequestWithUser =
            user match {
                case Some(user) =>
                    val newState = BatchRequestState.Created
                    val actionInfo = ActionInfo(date, user.user.ldapDn, newState, newState, None)
                    val newHistory = Traversable(actionInfo)
                    batchRequest.copy(timeCreated = date, createdById = user.user._id, state = newState, history = newHistory.toSeq)
                case None => throw new AdaException("No logged user found")
            }
            id <- {
                repo.save(batchRequestWithUser)
            }
        } yield {
            val createAction = ActionGraph.createAction()

            val notification = NotificationInfo(
                creationDate = date,
                dataSetId = batchRequest.dataSetId,
                userRole = Role.Requester,
                fromState = createAction.fromState,
                toState = createAction.toState,
                possibleActions =  ActionGraph.apply.get(createAction.toState).get.map(_.action),
                createdByUser = user.get.user.ldapDn,
                targetUser = user.get.user.ldapDn,
                description = None,
                targetUserEmail = user.get.user.email,
                updateDate = date,
                getRequestUrl = getReadOnlyUrl(id),
                notificationType = NotificationType.Solicitation,
                updatedByUser = user.get.user.ldapDn,
                items = None
            )

            actionNotificationService.sendNotifications(Traversable(notification))
            id
        }
    }

    def performAction(
        requestId: BSONObjectID,
        action: RequestAction.Value,
        role: Role.Value,
        description: Option[String]
    ) = restrictAdminOrUserCustomAny(isRequestAllowed(requestId, Some(action), Some(role), false)) {
        implicit request => {
            val dateOfUpdate = new Date()
            for {
                existingRequest <- repo.get(requestId)
                user <- currentUser()
                allowedStateAction = getNextState(existingRequest.get.state, action)
                //          userHasAllowedRole = checkAssumedRoleCanDoAction(role, allowedStateAction.allowed)
                descriptionExistsIfRequired = checkDescriptionExists(allowedStateAction, description)
                userIdsMapping <- batchOrderService.getAllowedUserIds(existingRequest.get, None)
                usersToNotify <- retrieveUsersToNotify(userIdsMapping)
                fieldNames <- getRequestSettingsFieldNames(existingRequest.get.dataSetId)
                items <- getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId, fieldNames)
                (id, notifications) <- {
                    val batchRequestWithStateAndNotifications = user match {
                        case Some(currentUser) =>
                            val newState = allowedStateAction.toState
                            val actionInfo = ActionInfo(dateOfUpdate, currentUser.user.ldapDn, existingRequest.get.state, newState, description)
                            val updatedHistory: Traversable[ActionInfo] = existingRequest.get.history :+ actionInfo
                            val notifications: Traversable[NotificationInfo] = usersToNotify.flatten { case (role, users) => {
                                users.map { user =>
                                    val notificationType = getNotificationType(allowedStateAction, role)
                                    NotificationInfo(
                                        creationDate = existingRequest.get.timeCreated,
                                        dataSetId = existingRequest.get.dataSetId,
                                        userRole = role,
                                        fromState = allowedStateAction.fromState,
                                        toState = allowedStateAction.toState,
                                        possibleActions =  ActionGraph.apply.get(allowedStateAction.toState).get.map(_.action),
                                        createdByUser = usersToNotify.get(Role.Requester).get.toSeq(0).ldapDn,
                                        targetUser = user.ldapDn,
                                        description = description,
                                        targetUserEmail = user.email,
                                        updateDate = dateOfUpdate,
                                        getRequestUrl = getRequestUrlByNotificationType(notificationType, existingRequest.get._id.get, role),
                                        notificationType = notificationType,
                                        updatedByUser = currentUser.user.ldapDn,
                                        items = items
                                    )
                                }
                            }
                            }
                            (existingRequest.get.copy(state = newState, createdById = existingRequest.get.createdById, history = updatedHistory.toSeq), notifications)
                        case None => throw new AdaException("No logged user found")
                    }
                    repo.update(batchRequestWithStateAndNotifications._1).map(id => (id, batchRequestWithStateAndNotifications._2))
                }
            } yield {
                actionNotificationService.sendNotifications(notifications)
                activeRequestsListRedirect.flashing("success" -> "state of request updated with success")
            }
        }.recover(handleExceptions("request action"))
    }

    private def checkDescriptionExists(action: models.Action, description: Option[String]) = {
        if (action.commentNeeded && !description.isDefined) {
            throw new AdaException("Description not provided or not accepted '" + description + "'" + " for new state: " + action.toState)
        }
    }

    private def retrieveUsersToNotify(userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]): Future[Map[Role.Value, Traversable[User]]] = {
        val userIds: Seq[Option[BSONObjectID]] = userIdsMapping.flatMap(_._2.map(Some(_))).toSeq
        for {
            users <- getUsersByIds(userIds)
        }
            yield {
                userIdsMapping.map { case (role, userId) => (role, userId.map(id => users.get(id).get)) }
            }
    }

    private def getUsersByIds(userIds: Traversable[Option[BSONObjectID]]) = {
        userRepo.find(Seq(UserIdentity.name #-> userIds.toSeq)).map { users =>
            users.map(c => (c._id.get, c)).toMap
        }
    }

    private def getNotificationType(action: models.Action, role: Role.Value) = {
        action.notified.find(r => r == role) match {
            case Some(role) => {
                NotificationType.Advice
            }
            case None => {
                if (action.solicited == role) {
                    NotificationType.Solicitation
                } else {
                    NotificationType.Advice
                }
            }
        }
    }

    private def getRequestUrlByNotificationType(
        notificationType: NotificationType.Value,
        requestId: BSONObjectID,
        role: Role.Value
    )(implicit request: AuthenticatedRequest[_]) = {
        notificationType match {
            case NotificationType.Advice => getReadOnlyUrl(requestId)
            case NotificationType.Solicitation => getActionUrl(requestId, role)
        }
    }

    private def getReadOnlyUrl(requestId: BSONObjectID)(implicit request: RequestHeader) =
        routes.BatchOrderRequestController.get(requestId).absoluteURL()

    private def getRequestSettingsFieldNames(dataSetId: String) =
      for {
        requestSetting <- requestSettingRepo.find(Seq("dataSetId" #== dataSetId), limit = Some(1)).map(_.headOption)

        dsa = dsaf(dataSetId).getOrElse(throw new AdaException(s"Data set id '${dataSetId}' not found."))

        view <- requestSetting match {
          case Some(setting) => dsa.dataViewRepo.get(setting.viewId)
          case None => Future(None)
        }
      } yield
        view.map(_.tableColumnNames).getOrElse(Nil)

    private def isRequestAllowed(
        requestId: BSONObjectID,
        action: Option[RequestAction.Value] = None,
        assumedRole: Option[Role.Value] = None,
        readOnly: Boolean = false
    )(
        deadboltUser: DeadboltUser,
        request: AuthenticatedRequest[Any]
    ): Future[Boolean] = {
        for {
            existingRequestOption <- repo.get(requestId)
            allowedRoles = determineValidRoles(action, assumedRole, readOnly, existingRequestOption.get)
            userIdsMapping <- batchOrderService.getAllowedUserIds(existingRequestOption.get, None)
        } yield {
            val assumedRoleHasPermission = userIdsMapping.find(entry => {
                allowedRoles.contains(entry._1) && entry._2.toSeq.contains(deadboltUser.id.get)
            }
            ).isDefined

            val assumedRoleMatchesUserRole = assumedRole match {
                case Some(role) => {
                    userIdsMapping.get(role).get.toSeq.contains(deadboltUser.id.get)
                }
                case None => true
            }

            assumedRoleHasPermission && assumedRoleMatchesUserRole
        }
    }

    private def determineValidRoles(
        action: Option[RequestAction.Value],
        assumedRole: Option[Role.Value],
        readOnly: Boolean,
        existingRequest: BatchOrderRequest
    ): Set[Role.Value] = {
        if (!readOnly) {
            action match {
                case Some(action) =>
                    assumedRole match {
                        case Some(role) => {
                            if (getNextState(existingRequest.state, action).allowed == role) {
                                Set(role)
                            } else {
                                Set()
                            }
                        }
                        case None => throw new AdaException("No role existing with action request " + action)
                    }
                case None => ActionGraph.apply.get(existingRequest.state).get.map(a => a.allowed).toSet
            }
        } else {
            Set(Role.Requester, Role.Owner, Role.Committee)
        }
    }

    private def getNextState(currentState: BatchRequestState.Value, action: RequestAction.Value): models.Action = {
        ActionGraph.apply.get(currentState).get.find(validAction => validAction.action == action) match {
            case Some(allowedAction) => allowedAction
            case None => throw new AdaException("Action '" + action + "' not allowed for current state '" + currentState + "'")
        }
    }

    private def getItemsById(
        itemIds: Seq[BSONObjectID],
        dataSetId: String,
        fieldNames: Traversable[String]
    ) = {
        val dsa: DataSetAccessor = dsaf(dataSetId).get
        val itemsRepo = dsa.dataSetRepo

        itemsRepo.find(Seq("_id" #-> itemIds), projection = fieldNames)
    }

    def action(id: BSONObjectID, role: Role.Value) = restrictAdminOrUserCustomAny(isRequestAllowed(id, None, Some(role), false)) {
        implicit request => {
            for {
                existingRequest <- repo.get(id)
                validActions = ActionGraph.apply.get(existingRequest.get.state).getOrElse(Traversable[models.Action]()).filter(_.allowed == role)
                editViewData <- existingRequest.fold(
                    throw new AdaException("request with id '" + id.stringify + "' not found")
                ) { entity =>
                    getEditViewData(id, entity)(request).map(Some(_))
                }
                fieldNames <- getRequestSettingsFieldNames(existingRequest.get.dataSetId)
                items <- getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId, fieldNames.toSeq)
            } yield {
                existingRequest match {
                    case None => NotFound(s"$entityName '${formatId(id)}' not found")
                    case Some(_) =>
                        implicit val context = dataSetWebContext(existingRequest.get.dataSetId)

                        if (validActions.size > 0) {
                            render {
                                case Accepts.Html() => Ok(views.html.requests.actions(editViewData.get._1.form.get, validActions, role, items, fieldNames))
                                case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
                            }
                        } else {
                            render {
                                case Accepts.Html() =>
                                    Redirect(routes.BatchOrderRequestController.get(id)).flashing("error" -> "No action can be performed at the moment.")
                                case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
                            }
                        }
                }
            }
        }.recover(handleEditExceptions(id))
    }

    override protected def getFormEditViewData(
        requestId: BSONObjectID,
        form: Form[BatchOrderRequest]
    ) = { implicit request =>
        getRequestWithItems(requestId, form).map(r => (IdForm(requestId, r._1), r._2, r._3))
    }

    private def getRequestWithItems(
        requestId: BSONObjectID,
        form: Form[BatchOrderRequest]
    )(
        implicit request: AuthenticatedRequest[_]
    ): Future[(Form[BatchOrderRequest], Traversable[JsObject], Traversable[String])] =
        for {
            existingRequest <- repo.get(requestId)
            fieldNames <- getRequestSettingsFieldNames(existingRequest.get.dataSetId)
            items <- getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId, fieldNames.toSeq)
        } yield {
            (form, items, fieldNames)
        }

    override protected def getFormShowViewData(
        requestId: BSONObjectID,
        form: Form[BatchOrderRequest]
    ) = { implicit request =>
        getRequestWithItems(requestId, form).map(r => (r._1.get, r._2, r._3))
    }

    override protected def getListViewData(page: Page[BatchOrderRequest], conditions: Seq[FilterCondition]) = { implicit request =>
        for {
            currentUser <- currentUser()
            users <- getUsersByIds(page.items.map(_.createdById))
            userRolesByRequest <- batchOrderService.getUserRolesByRequest(page.items, currentUser)
        } yield {
            val itemsForUserWithCall = buildItemsWithUserAndCall(page.items, users, currentUser, userRolesByRequest)
            (Page(itemsForUserWithCall, page.page, page.offset, page.total, page.orderBy), conditions)
        }
    }

    private def buildItemsWithUserAndCall(
        items: Traversable[BatchOrderRequest],
        users: Map[BSONObjectID, User],
        currentUser: Option[USER],
        rolesByRequestId: Map[BSONObjectID, Traversable[Role.Value]]
    ) = {
        items.map(item => (item, users.get(item.createdById.get).get.ldapDn, itemViewRouting(item, currentUser, rolesByRequestId.get(item._id.get).get)))
    }

    override protected def showView = { implicit ctx =>
        (views.html.requests.show(_, _, _)).tupled
    }

    override protected def editView = { implicit ctx =>
        (views.html.requests.edit(_, _, _)).tupled
    }

    override protected def listView = { implicit ctx =>
        (views.html.requests.list(_, _)).tupled
    }

    override protected def createView = { implicit ctx =>
        views.html.requests.create(_)
    }
}