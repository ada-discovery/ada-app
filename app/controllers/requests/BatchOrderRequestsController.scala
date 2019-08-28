package controllers.requests

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.BatchOrderRequest.batchRequestFormat
import models.{NotificationInfo, NotificationType, Role, _}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, Filter, User}
import org.ada.server.models.Filter.FilterOrId
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.FilterConditionExtraFormats.coreFilterConditionFormat
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.{DataSetWebContext, TableViewData}
import org.ada.web.models.security.DeadboltUser
import org.ada.web.security.AdaAuthConfig
import org.ada.web.services.DataSpaceService
import org.incal.core.dataaccess.Criterion._
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.{Criterion, Sort}
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.formatters.EnumFormatter
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Call, RequestHeader}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, RequestSettingRepo}
import services.UserProviderService
import services.request.{ActionGraph, _}
import views.html.dataset.actionTable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Deprecated
// TODO: whooooo, 17 dependent components, that's a bit too much, most of the funs from the "providers" should be put inside the controller since there are merely util funds (single caller, not general enough)
// TODO: For truly general funs you can create a single service called BatchOrderService and put everything there (rather than spliting it to 10 dummy classes)
class BatchOrderRequestsController @Inject()(
  requestsRepo: BatchOrderRequestRepo,
  requestSettingRepo: RequestSettingRepo,
  dataSetSettingRepo: DataSetSettingRepo,
  dsaf: DataSetAccessorFactory,
  val userManager: UserManager,
  val actionNotificationService: ActionNotificationService,
  roleService: RoleProviderService,
  userIdByRoleProvider: UserIdByRoleProviderImpl,
  itemsProvider: DataSetItemsProvider,
  dataSpaceService: DataSpaceService,
  userProvider: UserProviderService
) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo)
  with SubjectPresentRestrictedCrudController[BSONObjectID]
  with HasBasicFormCreateView[BatchOrderRequest]
  with HasEditView[BatchOrderRequest, BSONObjectID]
  with HasListView[BatchOrderRequest]
  with AdaAuthConfig { // TODO: No need for AdaAuthConfig
   private implicit val idsFormatter = BSONObjectIDStringFormatter
   private implicit val requestStateFormatter = EnumFormatter(BatchRequestState)
   private val activeRequestsListRedirect = Redirect(routes.BatchOrderRequestsController.findActive())
   private val selectedIdsForm = Form(single("selectedIds" -> seq(of[BSONObjectID])))

  override protected val homeCall = routes.BatchOrderRequestsController.findActive()
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

  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  def createNew(dataSet: String)
  = AuthAction {

    implicit request =>
      dsaf(dataSet).map { dsa =>

        for {
          // get the batch-order setting for a given data set
          setting <- requestSettingRepo.find(Seq("dataSetId" #== dataSet)).map(_.headOption)

          // handy things for a view: data set name, data space tree, and data set setting
          (dataSetName, dataSpaceTree, dataSetSetting) <- getDataSetNameTreeAndSetting(dsa)
        } yield
          setting match {
            case None =>
              NotFound(s"Batch-order setting for the data set '${dataSet}' not found.")

            case Some(setting) =>
              implicit val context = dataSetWebContext(dataSet)

              Ok(
                actionTable(
                  setting.displayFieldNames,
                  routes.BatchOrderRequestsController.saveNew(dataSet),
                  "Request Items",
                  dataSetName + " Batch Order Request",
                  Filter(),
                  dataSetSetting,
                  dataSpaceTree
                )
              )
          }
      }.getOrElse(
        Future(NotFound(s"Data set '${dataSet}' doesn't exist."))
      )
  }



  def saveNew(
    dataSet: String
  ) = AuthAction { implicit request =>
    selectedIdsForm.bindFromRequest.fold(
      _ =>
        Future(BadRequest(s"Cannot parse selected ids.")),

      selectedIds =>
        for {
          // get a currently-logged user
          user <- currentUser(request)

          // create a new batch-order request and save
          id <- repo.save(BatchOrderRequest(
            dataSetId = dataSet,
            itemIds = selectedIds,
            state = BatchRequestState.Created,
            createdById = user.flatMap(_._id)
          ))
        } yield {
          Redirect(homeCall).flashing("success" -> s"New batch-order request '${id.stringify}' has been created.")
        }
    )
  }

  override def get(id: BSONObjectID) =
    restrictAdminOrUserCustomAny(isRequestAllowed(id,None, None ,true))(toAuthenticatedAction(super.get(id)))

  override def edit(id: BSONObjectID) =
    restrictAdminAny(noCaching = true) {
      toAuthenticatedAction(super.edit(id))
    }

  override def update(id: BSONObjectID) =
    restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.update(id)))

  override def delete(id: BSONObjectID): Action[AnyContent] =
    restrictAdminAny(noCaching = true) {
      toAuthenticatedAction(super.delete(id))
    }

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) =
    restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.find(page, orderBy, filter)))

  def findItemsWithFilter(
    dataSetId: String,
    page: Int,
    orderBy: String,
    filterOrId: FilterOrId,
    filterOrIds: Seq[FilterOrId]
  ) =  restrictSubjectPresentAny(noCaching = true) {
    implicit request => {
      for {
        fieldNames <- getFieldNames(dataSetId)
        (tableViewData, newFilter) <- itemsProvider.retrieveTableWithFilterForSelection(page, dataSetId, orderBy, filterOrIds, Some(filterOrId), fieldNames.toSeq, None)
      } yield {
        val tableJson = views.html.requests.itemsPaginatedTable(dataSetId,tableViewData, tableViewData.page, tableViewData.filter, tableViewData.tableFields, isAjaxRefresh = true)
        val conditionPanel = views.html.filter.conditionPanel(Some(newFilter))
        val filterModel = Json.toJson( tableViewData.filter.get.conditions)

        Ok(Json.obj(
          "conditionPanel" -> conditionPanel.toString(),
          "filterModel" -> filterModel,
          "table" -> tableJson.toString()
        ))
      }
    }
  }

  def findItems(
                           dataSetId: String,
                           page: Int,
                           orderBy: String,
                           filterOrId: FilterOrId,
                           filterOrIds: Seq[FilterOrId]
                         ) =  restrictSubjectPresentAny(noCaching = true) {
    implicit request => {
      for {
        fieldNames <- getFieldNames(dataSetId)
        (tableViewData, newFilter) <- itemsProvider.retrieveTableWithFilterForSelection(page, dataSetId, orderBy, filterOrIds, Some(filterOrId), fieldNames.toSeq, None)
      } yield {
        val tableJson = views.html.requests.itemsPaginatedTable(dataSetId,tableViewData, tableViewData.page, tableViewData.filter, tableViewData.tableFields, isAjaxRefresh = true)
          Ok(tableJson)
      }
    }
  }

  def getFieldNames(dataSetId: String) =
    for {
      fieldNamesByDataSet <- requestSettingRepo.find(Seq("dataSetId" #== dataSetId)).map{ _.flatMap(_.displayFieldNames)}
    } yield
      fieldNamesByDataSet


  def findActiveWithFilter(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] = {
    restrictAny(findRequestsForUserWithFilter(pageCreated, pageToApprove, orderBy, filter))
  }

  def findActive(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] = {
    restrictAny(findRequestsForUser(pageCreated, pageToApprove, orderBy, filter))
  }

  override def listAll(orderBy: String): Action[AnyContent] = {
   restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.listAll(orderBy)))
  }

  def getPageWithCriteria(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, criteria: Seq[Criterion[Any]], user: Option[User], approverCriterion: Seq[Criterion[Any]]) = {
   val requesterCreiteria = criteria ++ buildRequesterCriterion(user)
   val approverCriteria = criteria ++ approverCriterion

    if(pageCreated.isDefined) {
      (pageCreated.get, requesterCreiteria, PageType.Created, approverCriteria)
    } else if(pageToApprove.isDefined ) {
      (pageToApprove.get, approverCriteria, PageType.ToApprove, requesterCreiteria)
    } else {
      (0, criteria ++ requesterCreiteria, PageType.Created, approverCriteria)
    }
  }

  def findRequestsForUserWithFilter(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] = AuthAction { implicit request => {
    for {
      user <- currentUser(request)
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

  def findRequestsForUser(pageCreated: Option[Int] = None, pageToApprove: Option[Int] = None, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] = AuthAction { implicit request => {
    for {
      user <- currentUser(request)
      approverCriterion <- buildApproverCriterion(user)
      criteria <- toCriteria(filter)
      (page, userCriteria, pageType, backgroundListCriteria) = getPageWithCriteria(pageCreated, pageToApprove, orderBy, criteria, user, approverCriterion)
      (items, count, backgroundListCount) <- getFutureUserScopedItemsAndCounts(Some(page), orderBy, userCriteria, backgroundListCriteria)
      viewData <- getUserScopedListViewData(Page(items, page, page * pageLimit, count, orderBy), filter)(request)
    } yield {
          pageToApprove match {
            case Some(page) => Ok((views.html.requests.requestTable(viewData._1, filter, (p, o) => controllers.requests.routes.BatchOrderRequestsController.findActive(None, Some(p), o, filter), isAjaxRefresh = true)))
            case None => Ok((views.html.requests.requestTable(viewData._1, filter, (p, o) => controllers.requests.routes.BatchOrderRequestsController.findActive(Some(p), None, o, filter), isAjaxRefresh = true)))
          }
        }
  }.recover(handleFindExceptions)
  }

  def getFutureUserScopedItemsAndCounts(
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
      (items, count, backgroundPageCount) <- findItemsAndCounts(criteria, sort, projection, limit, skip, backgroundPageCriteria)
      users <- userProvider.getUsersByIds(items.map(_.createdById))
    } yield {
      val itemsWithName = items.map( i => (i, users.get(i.createdById.get).get.ldapDn))
      // TODO: What is this??? you've already passed 'sort' to get sorted data (obtained from 'orderBy') --> creted by name is not in the model
      orderBy match  {
        case "createdby" => (itemsWithName.toSeq.sortWith(_._2 < _._2), count, backgroundPageCount)
        case "-createdby" => (itemsWithName.toSeq.sortWith(_._2 > _._2), count, backgroundPageCount)
        case _ => (itemsWithName, count,  backgroundPageCount)
      }
    }
    }

  // TODO: move inners of the fun to the caller
  def findItemsAndCounts(criteria: Seq[Criterion[Any]], sort: Seq[Sort],  projection: Seq[String], limit: Option[Int], skip: Option[Int], backgroundPageCriteria :Seq[Criterion[Any]] )={
    for{
      items <- repo.find(criteria, sort, projection, limit, skip)
      count <- repo.count(criteria)
      backgroundCount <- repo.count(backgroundPageCriteria)
    } yield{
      (items, count, backgroundCount)
    }
  }

  override def saveCall(
    batchRequest: BatchOrderRequest)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] = {
    val date = new Date()
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
      val createAction = ActionGraph.createAction()

      val notification = NotificationInfo(
        creationDate = date,
        dataSetId = batchRequest.dataSetId,
        userRole = Role.Requester,
        fromState = createAction.fromState,
        toState = createAction.toState,
        createdByUser = user.get.ldapDn,
        targetUser=user.get.ldapDn,
        description = None,
        targetUserEmail = user.get.email,
        updateDate = date,
        getRequestUrl = getReadOnlyUrl(id),
        notificationType= NotificationType.Solicitation,
        updatedByUser = user.get.ldapDn,
        items = None
        //TableViewData(Page(Traversable(),0,0,0,""),None,Traversable())
      )

      actionNotificationService.sendNotifications(Some(Traversable(notification)))
      id
    }
  }


  def buildHistory(currentHistory: Seq[ActionInfo], actionInfo: ActionInfo): Seq[ActionInfo] = {
    if(!currentHistory.isEmpty) {
        currentHistory :+ actionInfo
      } else {
      List(actionInfo)
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
      val isIncluded =  userIdsMapping.values.flatMap(ids => ids.toSeq).toSeq.contains(deadboltUser.id.get)

      assumedRole match {
        case Some(role) => {
          userIdsMapping.get(role).get.toSeq.contains(deadboltUser.id.get)
        }
        case None => isIncluded
      }
    }
  }

  def buildApproverCriterion(user: Option[User]) = {
    for {
      committees <- requestSettingRepo.find()
      dataSetSettings <- dataSetSettingRepo.find(Seq("ownerId" #== user.get._id))
    }
      yield {
        val userCommittee = committees.filter(c => c.userIds.contains(user.get._id.get))
        val committeeDataSetIds = userCommittee.map(c=>c.dataSetId).toSeq
        val ownedDataSetIds = dataSetSettings.map(s=>s.dataSetId).toSeq

        val dataSetIds = ownedDataSetIds ++ committeeDataSetIds

        Seq("dataSetId" #-> dataSetIds, "state" #!= BatchRequestState.Created.toString)
      }
  }

  def buildRequesterCriterion(user: Option[User]) = {
    Seq("createdById" #== user.get._id)
  }

  def determineValidRoles(
    action: Option[RequestAction.Value],
    role: Option[Role.Value],
    readOnly: Boolean,
    existingRequest: BatchOrderRequest
  ): Set[Role.Value] = {
    if(!readOnly) {
        action match {
          case Some(action) =>
            role match {
              case Some(role) => {
                if(getNextState(existingRequest.state, action).allowed == role){
                  Set(role)
                } else{
                   Set()
                }
              }
                case None => throw new AdaException("No role existing with action request "+ action)
              }
          case None => {
            ActionGraph.apply.get(existingRequest.state).get.map(a => a.allowed).toSet
          }
        }
      } else {
      Set(Role.Requester, Role.Owner, Role.Committee)
    }
  }

  def checkAssumedRoleCanDoAction(
    assumedRole: Role.Value,
    allowedRole: Role.Value
  ) =
    if(assumedRole != allowedRole) {
       throw new AdaException("Action not allowed")
    }

  def performAction(requestId: BSONObjectID, action: RequestAction.Value, role: Role.Value, description: Option[String]) = restrictAdminOrUserCustomAny(isRequestAllowed(requestId, Some(action), Some(role), false)) {
    implicit request => {
      var notifications = Traversable[NotificationInfo]()
      for {
        existingRequest <- repo.get(requestId)
        itemsExist =  checkItemsExist(existingRequest.get)
        user <- currentUser(request)
        allowedStateAction = {
          getNextState(existingRequest.get.state, action)
        }
       userHasAllowedRole =  checkAssumedRoleCanDoAction(role, allowedStateAction.allowed)
        descriptionExists = {
          checkDescriptionExists(allowedStateAction, description)
        }
        userIdsMapping <- userIdByRoleProvider.getIdByRole(existingRequest.get, None)
        usersToNotify <- retrieveUsersToNotify(userIdsMapping)
        fieldNames <- getFieldNames(existingRequest.get.dataSetId)
        items <- itemsProvider.getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId, fieldNames)
        id <- {
          val batchRequestWithState = user match {
            case Some(currentUser) =>
              val dateOfUpdate = new Date()
              val newState = allowedStateAction.toState
              val actionInfo = buildActionInfo(dateOfUpdate, currentUser.ldapDn, existingRequest.get.state, newState, description)
              val updatedHistory = buildHistory(existingRequest.get.history, actionInfo)

             notifications = usersToNotify.flatten {case (role, users) => {
              users.map{ user=>
                val notificationType = getNotificationType(allowedStateAction, role)
                NotificationInfo(
                  creationDate = existingRequest.get.timeCreated,
                  dataSetId = existingRequest.get.dataSetId,
                  userRole = role,
                  fromState = allowedStateAction.fromState,
                  toState = allowedStateAction.toState,
                  createdByUser = usersToNotify.get(Role.Requester).get.toSeq(0).ldapDn,
                  targetUser = user.ldapDn,
                  description = description,
                  targetUserEmail = user.email,
                  updateDate = dateOfUpdate,
                  getRequestUrl = getUrlType(notificationType, existingRequest.get._id.get, role),
                  notificationType = notificationType,
                  updatedByUser = currentUser.ldapDn,
                  items = items.map(i => i._1)//.get
                )
              }
            }}

              existingRequest.get.copy(state = newState, createdById = existingRequest.get.createdById, history = updatedHistory)
            case None => throw new AdaException("No logged user found")
          }
          repo.update(batchRequestWithState)
        }
      } yield {
        id
      actionNotificationService.sendNotifications(Some(notifications))
        activeRequestsListRedirect.flashing("success" -> "state of request updated with success")
      }
    }.recover(handleExceptions("request action"))
  }

  def checkItemsExist(request: BatchOrderRequest)=
    if(request.itemIds.size == 0) {
      throw new AdaException("No item in this request, please select items before submitting")
    }

  def checkDescriptionExists(action: models.Action, description: Option[String]) = {
    if(action.commentNeeded && !description.isDefined ) {
         throw new AdaException("Description not provided or not accepted '" + description + "'" + " for new state: " + action.toState)
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

  def getNotificationType(action: models.Action, role: Role.Value)= {
    action.notified.find(r => r == role) match {
      case Some(role) => {
       NotificationType.Advice
      }
      case None => {
        if(action.solicited == role){
            NotificationType.Solicitation
          } else {
            NotificationType.Advice
        }
      }
    }
  }

  def getUrlType(notificationType : NotificationType.Value, requestId: BSONObjectID, role: Role.Value)(implicit request: AuthenticatedRequest[_]) = {
    notificationType match {
      case NotificationType.Advice => getReadOnlyUrl(requestId)
      case NotificationType.Solicitation => getActionUrl(requestId, role)
    }
  }

  override protected type EditViewData = (
    IdForm[BSONObjectID, BatchOrderRequest],
    Option[TableViewData]
  )

  override protected type ListViewData = (
    Page[(BatchOrderRequest, String, Call)],
    Seq[FilterCondition]
  )

  override protected type ShowViewData = (
    BatchOrderRequest,
    Option[TableViewData]
  )

  protected type UserScopedListViewData = (
    Page[(BatchOrderRequest, String, Call)],
    Seq[FilterCondition]
  )

  override protected def getFormEditViewData(
    requestId: BSONObjectID,
    form: Form[BatchOrderRequest]
  ) = { implicit request =>
    getRequestWithItems(requestId, form).map(r => (IdForm(requestId, r._1), r._2))
  }

  override protected def getFormShowViewData(
    requestId: BSONObjectID,
    form: Form[BatchOrderRequest]
  ) = { implicit request =>
    getRequestWithItems(requestId, form).map(r=>(r._1.get,r._2))
  }

  def getRequestWithItems(
    requestId: BSONObjectID,
    form: Form[BatchOrderRequest])(
    implicit request: AuthenticatedRequest[_]
  ): Future[(Form[BatchOrderRequest], Option[TableViewData])] =
    for {
      existingRequest <- repo.get(requestId)
      fieldNames <- getFieldNames(existingRequest.get.dataSetId)
      items <- itemsProvider.getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId, fieldNames.toSeq)
    } yield {
      (form, items.map(i=>i._1))
    }

  protected def getUserScopedListViewData(
    page: Page[(BatchOrderRequest, String)],
    conditions: Seq[FilterCondition]
  ): AuthenticatedRequest[_] => Future[UserScopedListViewData] = {
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

  override protected def editView = { implicit ctx =>{
    (views.html.requests.edit(_, _)).tupled
  }

  }

  def action(id: BSONObjectID, role: Role.Value) = restrictAdminOrUserCustomAny(isRequestAllowed(id, None, Some(role),false)) {
    implicit request => {
      for {
        existingRequest <- repo.get(id)
        validActions = ActionGraph.apply.get(existingRequest.get.state).getOrElse(Traversable[models.Action]()).filter(_.allowed == role)
        editViewData <- existingRequest.fold(
          throw new AdaException("request with id '" + id.stringify + "' not found")
        ) { entity =>
          getEditViewData(id, entity)(request).map(Some(_))
        }
        fieldNames <- getFieldNames(existingRequest.get.dataSetId)
        items <- itemsProvider.getItemsById(existingRequest.get.itemIds, existingRequest.get.dataSetId,fieldNames.toSeq)
      } yield {
        existingRequest match {
          case None => NotFound(s"$entityName '${formatId(id)}' not found")
          case Some(_) =>

            if (validActions.size > 0) {
              render {
                case Accepts.Html() => Ok(views.html.requests.actions(editViewData.get._1.form.get, validActions, role, items))
                case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
              }
            } else {
              render {
                case Accepts.Html() =>
                  Redirect(routes.BatchOrderRequestsController.get(id)).flashing("error" -> "No action can be performed at the moment.")
                case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
              }
            }
        }
      }
    }.recover(handleEditExceptions(id))
  }

  def itemViewRouting(request: BatchOrderRequest, user: Option[User], roles: Traversable[Role.Value]): Call = {
    val userRolesPrioritized =  PrioritizedRoles.roles.toSeq.sortBy(_._1).filter(p => roles.toSeq.contains(p._2))
    val applicableActions = ActionGraph.apply.get(request.state).getOrElse(Traversable())
    val allowedRoles = applicableActions.map(a => a.allowed).toSet
    val headRole = userRolesPrioritized.toStream.map(p => p._2).find(p => allowedRoles.contains(p) || p == Role.Administrator).headOption

    headRole match {
      case Some(role) => {
        if(role == Role.Administrator) {
          controllers.requests.routes.BatchOrderRequestsController.edit(request._id.get)
        } else {
          controllers.requests.routes.BatchOrderRequestsController.action(request._id.get, role)
        }
      }
      case None => controllers.requests.routes.BatchOrderRequestsController.get(request._id.get)
    }
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

  override protected def listView = { implicit ctx =>
    (views.html.requests.list(_, _)).tupled
  }

  def getReadOnlyUrl(requestId: BSONObjectID)(implicit request: RequestHeader) =
    routes.BatchOrderRequestsController.get(requestId).absoluteURL()

  def getActionUrl(requestId: BSONObjectID, role: Role.Value)(implicit request: RequestHeader) =
    routes.BatchOrderRequestsController.action(requestId, role).absoluteURL()

  def checkUserHasRoles(user: Option[User], assumingRole: Option[Role.Value], validRoles: Set[Role.Value], userIdsMapping: Map[Role.Value, Traversable[BSONObjectID]]) =
    user match {
      case Some(currentUser) => {
        assumingRole match {
          case Some(someRole) => userIdsMapping.toSeq.contains(currentUser._id.get) && validRoles.filter(role => someRole == role).size == 1
          case None =>  userIdsMapping.values.filter(ids=>ids.toSet.contains(currentUser._id.get)).size >= 1
        }
      }
      case None => throw new AdaException("No logged user found")
    }

}