package controllers.orderrequest

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.orderrequest.routes.{BatchOrderRequestController => orderRoutes}
import javax.inject.Inject
import models.BatchOrderRequest.batchRequestFormat
import models.{NotificationInfo, NotificationType, Role, _}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.models.User.UserIdentity
import org.ada.server.models._
import org.ada.server.field.FieldUtil._
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.controllers.dataset.{DataSetViewHelper, DataSetWebContext, TableViewData}
import org.ada.web.models.security.DeadboltUser
import org.ada.web.services.DataSpaceService
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.play.{Page, PageOrder}
import org.incal.play.controllers._
import org.incal.play.formatters.EnumFormatter
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import org.incal.play.util.WebUtil.toSort
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText, _}
import play.api.libs.json.JsObject
import play.api.mvc.{Action => _, _}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, BatchOrderRequestSettingRepo}
import services.request.{ActionNotificationService, BatchOrderService}
import services.request.ActionGraph
import views.html.dataset.view.actionView
import views.html.{requests => views}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BatchOrderRequestController @Inject()(
    requestsRepo: BatchOrderRequestRepo,
    requestSettingRepo: BatchOrderRequestSettingRepo,
    dataSetSettingRepo: DataSetSettingRepo,
    dsaf: DataSetAccessorFactory,
    val actionNotificationService: ActionNotificationService,
    batchOrderService: BatchOrderService,
    val dataSpaceService: DataSpaceService,
    userRepo: UserRepo
) extends AdaCrudControllerImpl[BatchOrderRequest, BSONObjectID](requestsRepo) // maybe switch to readonly controller ???
    with AdminRestrictedCrudController[BSONObjectID]
    with HasBasicFormCreateView[BatchOrderRequest]
    with HasEditView[BatchOrderRequest, BSONObjectID]
    with HasListView[BatchOrderRequest]
    with DataSetViewHelper {

  private implicit val idsFormatter = BSONObjectIDStringFormatter
  private implicit val requestStateFormatter = EnumFormatter(BatchRequestState)

  override protected val homeCall = orderRoutes.findActive()

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "itemIds" -> seq(of[BSONObjectID]),
      "state" -> of[BatchRequestState.Value],
      "created by id" -> ignored(BSONObjectID.generate),
      "timeCreated" -> ignored(new Date()),
      "history" -> ignored(Seq[ActionInfo]())
    )(BatchOrderRequest.apply)(BatchOrderRequest.unapply)
  )

  override protected lazy val entityName = "Order Request"

  override protected def formatId(id: BSONObjectID) = id.stringify

  private def dataSetWebContext(dataSetId: String)(implicit context: WebContext) = DataSetWebContext(dataSetId)

  private val activeRequestsListRedirect = Redirect(homeCall)
  private val selectedIdsForm = Form(single("selectedIds" -> seq(of[BSONObjectID])))

  // List - admin only

  override protected type ListViewData = (
    Page[(BatchOrderRequest, Option[String], Call)],
    Seq[FilterCondition]
  )

  override protected def getListViewData(
    page: Page[BatchOrderRequest],
    conditions: Seq[FilterCondition]
  ) = { implicit request =>
    for {
      // get the currently-logged user
      userOption <- currentUser()

      // check if the user exists
      user = requireDefined(userOption, s"No current user found.").user

      // get a map containing id -> user associations
      users <- getIdUserMap(page.items.map(_.createdById))

      // get a map with request id -> user roles assocs
      requestIdUserRolesMap <- batchOrderService.getRequestIdUserRoles(page.items, user)
    } yield {

      val itemsForUserWithCall = page.items.map { orderRequest =>
        val userName = users.get(orderRequest.createdById).map(_.userId)
        val roles = requestIdUserRolesMap.get(orderRequest._id.get).getOrElse(
          throw new IllegalStateException(s"No roles found for the batch order request ${orderRequest._id.get.stringify}.")
        )
        val routing = itemViewRouting(orderRequest, roles)

        (orderRequest, userName, routing)
      }

      (Page(itemsForUserWithCall, page.page, page.offset, page.total, page.orderBy), conditions)
    }
  }

  override protected def listView = { implicit ctx =>
    (views.list(_, _)).tupled
  }

  // Create

  def createNew(dataSet: String) =
    restrictAdminOrPermissionAny(orderRequestPermission(dataSet), noCaching = true) { implicit request =>
    {
      dsaf(dataSet).map { dsa =>
        for {
          // handy things for a view: data set name, data space tree, and data set setting
          (dataSetName, dataSpaceTree, dataSetSetting) <- getDataSetNameTreeAndSetting(dsa)

          // get the batch-order setting for a given data set
          settingOption <- requestSettingRepo.find(Seq("dataSetId" #== dataSet)).map(_.headOption)

          // check if the setting is actually defined
          setting = requireDefined(settingOption, s"Batch-order setting for the data set '${dataSet}' not found.")

          // get the view linked with this setting
          dataViewOption <- dsa.dataViewRepo.get(setting.viewId)

          // check if the data view is actually defined
          dataView = requireDefined(dataViewOption, s"Data view '${setting.viewId}' of the data set '${dataSet}' not found.")

          // load all the filters (if none - create a dummy one)
          resolvedFilters <- Future.sequence(dataView.filterOrIds.map(dsa.filterRepo.resolve)).map { filters =>
            if (filters.isEmpty) Seq(org.ada.server.models.Filter()) else filters
          }

          // collect the filters' conditions
          conditions = resolvedFilters.map(_.conditions)

          // convert the conditions to criteria
          criteria <- Future.sequence(conditions.map(toCriteria))

          // table column names and widget specs
          tableColumnNames = dataView.tableColumnNames

          // initialize table pages
          tablePagesToUse = Seq.fill(resolvedFilters.size)(PageOrder(0, ""))

          // create a name -> field map of all the referenced fields for a quick lookup
          nameFieldMap <- createNameFieldMap(dsa.fieldRepo)(conditions, Nil, tableColumnNames)

          // get the view table data
          viewTableResponses <-
            Future.sequence(
              (tablePagesToUse, criteria, resolvedFilters).zipped.map { case (tablePage, criteria, resolvedFilter) =>
                getInitViewResponse(dsa.dataSetRepo)(
                  tablePage.page, tablePage.orderBy, resolvedFilter, criteria, nameFieldMap, tableColumnNames, pageLimit
                )
              }
            )
        } yield {
          implicit val context = dataSetWebContext(dataSet)

          // init view parts - table view data
          val tableViewData = (viewTableResponses, tablePagesToUse).zipped.map {
            case (viewResponse, tablePage) =>
              val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * pageLimit, viewResponse.count, tablePage.orderBy)
              TableViewData(newPage, Some(viewResponse.filter), viewResponse.tableFields)
          }

          val itemName = dataSetSetting.displayItemName.getOrElse(dataSetName + " Item")

          Ok(
            actionView(
              orderRoutes.saveNew(dataSet),
              "Request Items",
              dataSetName + " Batch Order Request",
              itemName,
              setting.viewId,
              tableViewData,
              dataView.elementGridWidth,
              dataSetSetting,
              dataSpaceTree
            )
          )
        }
      }.getOrElse(
        Future(NotFound(s"Data set '${dataSet}' doesn't exist."))
      )
    }.recover(handleExceptions("a create batch-order request"))
  }

  // standard create action and create view we don't use
  override protected def createView = ???

  // Save

  def saveNew(dataSet: String) =
    restrictAdminOrPermissionAny(orderRequestPermission(dataSet), true) { implicit request =>
      {
        selectedIdsForm.bindFromRequest.fold(
          _ =>  Future(BadRequest(s"Cannot parse selected ids.")),
        selectedIds =>
          if (selectedIds.isEmpty) {
            Future(Redirect(homeCall).flashing("error" -> "No item(s) added to the request."))
          } else {
            for {
              // get the currently-logged user
              userOption <- currentUser()

              // check if the user exists
              user = requireDefined(userOption, "No current user found.").user

              userId = requireDefined(user._id, "User has no id.")

              // create a new batch-order request and save
              id <- {
                val batchRequest = BatchOrderRequest(
                  dataSetId = dataSet,
                  itemIds = selectedIds,
                  state = BatchRequestState.Created,
                  createdById = userId,
                  history = Seq(ActionInfo(BatchRequestState.Created, BatchRequestState.Created, user.userId))
                )

                repo.save(batchRequest)
              }
            } yield {
              val date = new Date()
              val createAction = ActionGraph.createAction

              val notification = NotificationInfo(
                creationDate = date,
                dataSetId = dataSet,
                userRole = Role.Requester,
                fromState = createAction.fromState,
                toState = createAction.toState,
                possibleActions =  ActionGraph.asMap.get(createAction.toState).get.map(_.action),
                createdByUser = user.userId,
                targetUser = user.userId,
                description = None,
                targetUserEmail = user.email,
                updateDate = date,
                getRequestUrl = orderRoutes.action(id, Role.Requester).absoluteURL(),  // orderRoutes.get(id).absoluteURL(),
                notificationType = NotificationType.Solicitation,
                updatedByUser = user.userId,
                items = None
              )

              actionNotificationService.sendNotifications(Seq(notification))
              Redirect(homeCall).flashing("success" -> s"New batch-order request '${id.stringify}' has been created.")
            }
          }
        )
      }.recover(handleExceptions("a save (new) batch-order request"))
    }

  private def orderRequestPermission(dataSet: String) = s"DS:$dataSet:createRequest"

  // Show - admin only

  override protected type ShowViewData = (
    BatchOrderRequest,
    Traversable[Seq[String]],
    Seq[String]
  )

  override protected def getFormShowViewData(
    requestId: BSONObjectID,
    form: Form[BatchOrderRequest]
  ) = { implicit request =>
    getRequestedItemsWithFieldLabels(requestId).map { case (itemsToDisplay, fieldLabels) =>
      val batchOrderRequest = requireDefined(form.value, "No batch order request attached to the show view.")
      (batchOrderRequest, itemsToDisplay, fieldLabels)
    }
  }

  /**
    * Auxiliary function to get the requested items in a display-ready format and field labels for the batch order request with a given id.

    * @param requestId
    * @param request
    * @return
    */
  private def getRequestedItemsWithFieldLabels(
    requestId: BSONObjectID)(
    implicit request: AuthenticatedRequest[_]
  ): Future[(Traversable[Seq[String]], Seq[String])] =
    for {
      existingRequest <- repo.get(requestId)

      // check if the batch order request is actually defined
      orderRequest = requireDefined(existingRequest, s"Batch order request with the id '${requestId.stringify}' not found.")

      // get a data set accessor
      dsa = requireDefined(dsaf(orderRequest.dataSetId), s"Data set '${orderRequest.dataSetId}' not found.")

      // get the fields associated with this request's setting
      fields <- getRequestFields(dsa)

      // get the items by their ids
      items <- if (orderRequest.itemIds.nonEmpty) {
        dsa.dataSetRepo.find(Seq(JsObjectIdentity.name #-> orderRequest.itemIds), projection = fields.map(_.name))
      } else Future(Nil)
    } yield {
      val namedFieldTypes = fields.map(_.toNamedTypeAny).toSeq
      val itemsToDisplay = items.map(_.toDisplayStrings(namedFieldTypes))
      (itemsToDisplay, fields.map(_.labelOrElseName))
    }

  /**
    * Returns field names to display for batch order requests associated with a given data set
    *
    * @param dsa
    * @return
    */
  private def getRequestFields(dsa: DataSetAccessor): Future[Seq[Field]] =
    for {
      requestSetting <- requestSettingRepo.find(Seq("dataSetId" #== dsa.dataSetId), limit = Some(1)).map(_.headOption)

      // check if the batch order request setting is actually defined
      setting = requireDefined(requestSetting, s"Batch order request setting for data set '${dsa.dataSetId}' not found.")

      // get the associated view
      viewOption <- dsa.dataViewRepo.get(setting.viewId)

      // check if the view is actually defined
      view = requireDefined(viewOption, s"View '${setting.viewId.stringify}' not found.")

      // find the fields reference by view (table column names)
      fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> view.tableColumnNames))
    } yield {
      val nameFieldMap = fields.map(field => (field.name, field)).toMap
      view.tableColumnNames.flatMap { fieldName => nameFieldMap.get(fieldName) }
    }

  override protected def showView = { implicit ctx =>
    (views.show(_, _, _)).tupled
  }

  // TODO: who can actually "see" this? admin and ..
  override def get(id: BSONObjectID) =
    restrictAdminOrUserCustomAny(isRequestAllowed(requestId = id, readOnly = true))(
      toAuthenticatedAction(super[AdaCrudControllerImpl].get(id))
    )

  // Edit - admin only

  override protected type EditViewData = (
    BSONObjectID,
    Form[BatchOrderRequest],
    Traversable[Seq[String]],
    Seq[String]
  )

  override protected def getFormEditViewData(
    requestId: BSONObjectID,
    form: Form[BatchOrderRequest]
  ) = { implicit request =>
    getRequestedItemsWithFieldLabels(requestId).map { case (items, fieldNames) =>
      (requestId, form, items, fieldNames)
    }
  }

  override protected def editView = { implicit ctx =>
    (views.edit(_, _, _, _)).tupled
  }

  // List Active

  protected type UserScopedListViewData = (
    Page[(BatchOrderRequest, Option[String], Call)],
    Seq[FilterCondition]
  )

  def findActive(
    pageCreated: Option[Int] = None,
    pageToApprove: Option[Int] = None,
    orderBy: String,
    filter: Seq[FilterCondition]
  ) = restrictAny { implicit request =>
    {
      for {
        userOption <- currentUser()

        user = requireDefined(userOption, s"No current user found.").user

        approverCriteria <- buildApproverCriteria(user)

        criteria <- toCriteria(filter)

        (page, userCriteria, pageType, backgroundListCriteria) = getPageWithCriteria(pageCreated, pageToApprove, orderBy, criteria, user, approverCriteria)

        (items, count, backgroundListCount) <- getUserScopedItemsAndCounts(Some(page), orderBy, userCriteria, backgroundListCriteria)

        page <- getUserScopedListViewData(Page(items, page, page * pageLimit, count, orderBy))
      } yield
        Ok((views.listWithTabs(page, pageType, filter, backgroundListCount)))

    }.recover(handleFindExceptions)
  }

  private def getPageWithCriteria(
    pageCreated: Option[Int] = None,
    pageToApprove: Option[Int] = None,
    orderBy: String,
    criteria: Seq[Criterion[Any]],
    user: User,
    approverCriteria: Seq[Criterion[Any]]
  ) = {
    val requesterCriteria = criteria ++ Seq("createdById" #== user._id)
    val fullApproverCriteria = criteria ++ approverCriteria

    if (pageCreated.isDefined) {
      (pageCreated.get, requesterCriteria, PageType.Created, fullApproverCriteria)
    } else if (pageToApprove.isDefined) {
      (pageToApprove.get, fullApproverCriteria, PageType.ToApprove, requesterCriteria)
    } else {
      (0, requesterCriteria, PageType.Created, fullApproverCriteria)
    }
  }

  private def buildApproverCriteria(user: User) =
    for {
      committees <- requestSettingRepo.find()
      dataSetSettings <- dataSetSettingRepo.find(Seq("ownerId" #== user._id))
    } yield {
      val userCommittee = committees.filter(c => c.committeeUserIds.contains(user._id.get))
      val committeeDataSetIds = userCommittee.map(c => c.dataSetId).toSeq
      val ownedDataSetIds = dataSetSettings.map(s => s.dataSetId).toSeq
      val dataSetIds = ownedDataSetIds ++ committeeDataSetIds
      Seq("dataSetId" #-> dataSetIds, "state" #!= BatchRequestState.Created.toString)
  }

  private def getUserScopedItemsAndCounts(
    page: Option[Int],
    orderBy: String,
    criteria: Seq[Criterion[Any]],
    backgroundPageCriteria: Seq[Criterion[Any]],
    limit: Option[Int] = Some(pageLimit),
    projection: Seq[String] = listViewColumns.getOrElse(Nil)
  ): Future[(Traversable[(BatchOrderRequest, Option[String])], Int, Int)] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) => page * limit }

    for {
      items <- repo.find(criteria, sort, projection, limit, skip)
      count <- repo.count(criteria)
      backgroundCount <- repo.count(backgroundPageCriteria)
      users <- getIdUserMap(items.map(_.createdById))
    } yield {
      val itemsWithName = items.map(i => (i, users.get(i.createdById).map(_.userId)))
      (itemsWithName, count, backgroundCount)
    }
  }

  protected def getUserScopedListViewData(
    page: Page[(BatchOrderRequest, Option[String])])(
    implicit request: AuthenticatedRequest[_]
  ) =
    for {
      // get the currently-logged user
      userOption <- currentUser()

      // check if the user exists
      user = requireDefined(userOption, "No current user found.").user

      userRolesByRequest <- batchOrderService.getRequestIdUserRoles(page.items.map(_._1), user)
    } yield {
      val pageItemsWithCall = page.items.map { item =>
        val call = itemViewRouting(item._1, userRolesByRequest.get(item._1._id.get).get)
        (item._1, item._2, call)
      }

      Page(pageItemsWithCall, page.page, page.offset, page.total, page.orderBy)
    }

  private def itemViewRouting(
    request: BatchOrderRequest,
    roles: Traversable[Role.Value]
  ): Call = {
    val userRolesPrioritized = PrioritizedRoles.apply.toSeq.sortBy(_._1).filter(p => roles.toSeq.contains(p._2))
    val applicableActions = ActionGraph.asMap.get(request.state).getOrElse(Traversable())
    val allowedRoles = applicableActions.map(a => a.allowed).toSet
    val headRole = userRolesPrioritized.toStream.map(p => p._2).find(p => allowedRoles.contains(p) || p == Role.Administrator).headOption

    headRole match {
      case Some(role) =>
        if (role == Role.Administrator)
          orderRoutes.edit(request._id.get)
        else
          orderRoutes.action(request._id.get, role)

      case None => orderRoutes.get(request._id.get)
    }
  }

  def getRequestTable(
    pageCreated: Option[Int] = None,
    pageToApprove: Option[Int] = None,
    orderBy: String,
    filter: Seq[FilterCondition]
  ) = restrictAny { implicit request =>
    {
      for {
        userOption <- currentUser()

        user = requireDefined(userOption, s"No current user found.").user

        approverCriteria <- buildApproverCriteria(user)

        criteria <- toCriteria(filter)

        (page, userCriteria, pageType, backgroundListCriteria) = getPageWithCriteria(pageCreated, pageToApprove, orderBy, criteria, user, approverCriteria)

        (items, count, backgroundListCount) <- getUserScopedItemsAndCounts(Some(page), orderBy, userCriteria, backgroundListCriteria)

        page <- getUserScopedListViewData(Page(items, page, page * pageLimit, count, orderBy))
      } yield
        pageToApprove match {
          case Some(_) =>
            Ok(views.toApproveRequestTable(page, filter))

          case None =>
            Ok(views.createdRequestTable(page, filter))
        }
    }.recover(handleFindExceptions)
  }

  def performAction(
    requestId: BSONObjectID,
    action: RequestAction.Value,
    role: Role.Value,
    description: Option[String]
  ) = restrictAdminOrUserCustomAny(isRequestAllowed(requestId, Some(action), Some(role), false)) { implicit request =>
    {
      for {
        requestOption <- repo.get(requestId)

        orderRequest = requireDefined(requestOption, s"Batch order request '${requestId.stringify}' not found.")

        userOption <- currentUser()

        user = requireDefined(userOption, s"No current user found.").user

        allowedStateAction = getNextState(orderRequest.state, action)

        // userHasAllowedRole = checkAssumedRoleCanDoAction(role, allowedStateAction.allowed)
        // descriptionExistsIfRequired = checkDescriptionExists(allowedStateAction, description)

        userIdsMapping <- batchOrderService.getAllowedUserIds(orderRequest)

        // id -> user map of all the users allowed to handle the request
        userIdMap <- {
          val userIds = userIdsMapping.flatMap(_._2)
          getIdUserMap(userIds)
        }

        roleUsersToNotify = userIdsMapping.map { case (role, userIds) =>
          (role, userIds.map(id => userIdMap.get(id).get))
        }

        // get a data set accessor
        dsa = requireDefined(dsaf(orderRequest.dataSetId), s"Data set '${orderRequest.dataSetId}' not found.")

        // get the fields associated with this request's setting
        fields <- getRequestFields(dsa)

        // get the items by their ids
        items <- dsa.dataSetRepo.find(Seq(JsObjectIdentity.name #-> orderRequest.itemIds), projection = fields.map(_.name))

        notifications = roleUsersToNotify.flatten { case (role, users) =>
          users.map { user =>
            val notificationType = getNotificationType(allowedStateAction, role)

            val notificationUrlToShow =
              notificationType match {
                case NotificationType.Advice => orderRoutes.get(requestId).absoluteURL()
                case NotificationType.Solicitation => orderRoutes.action(requestId, role).absoluteURL()
              }

            NotificationInfo(
              creationDate = orderRequest.timeCreated,
              dataSetId = orderRequest.dataSetId,
              userRole = role,
              fromState = allowedStateAction.fromState,
              toState = allowedStateAction.toState,
              possibleActions =  ActionGraph.asMap.get(allowedStateAction.toState).get.map(_.action),
              createdByUser = roleUsersToNotify.get(Role.Requester).get.toSeq(0).userId,
              targetUser = user.userId,
              description = description,
              targetUserEmail = user.email,
              updateDate = new Date(),
              getRequestUrl = notificationUrlToShow,
              notificationType = notificationType,
              updatedByUser = user.userId,
              items = items
            )
          }
        }

        // update the batch order request with new state and history
        _ <- {
          val newState = allowedStateAction.toState
          val actionInfo = ActionInfo(orderRequest.state, newState, user.userId, description)

          val updatedHistory = orderRequest.history :+ actionInfo
          val updatedOrderRequest = orderRequest.copy(state = newState, history = updatedHistory)

          repo.update(updatedOrderRequest)
        }
      } yield {
        actionNotificationService.sendNotifications(notifications)
        activeRequestsListRedirect.flashing("success" -> "state of request updated with success")
      }
    }.recover(handleExceptions("request action"))
  }

  // TODO: What is a difference between performAction and action
  def action(id: BSONObjectID, role: Role.Value) =
    restrictAdminOrUserCustomAny(isRequestAllowed(id, None, Some(role), false)) { implicit request =>
      {
        for {
          requestOption <- repo.get(id)

          orderRequest = requireDefined(requestOption, s"Batch order request '${id.stringify}' not found.")

          validActions = ActionGraph.asMap.get(orderRequest.state).getOrElse(Traversable[models.Action]()).filter(_.allowed == role)

          editViewData <-  getEditViewData(id, orderRequest)(request).map(Some(_))

          (items, fieldLabels) <- getRequestedItemsWithFieldLabels(id)
        } yield {
          implicit val context = dataSetWebContext(orderRequest.dataSetId)

          if (validActions.size > 0) {
            render {
              case Accepts.Html() => Ok(views.actions(editViewData.get._2.get, validActions, role, items, fieldLabels))
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
          } else {
            render {
              case Accepts.Html() =>
                Redirect(orderRoutes.get(id)).flashing("error" -> "No action can be performed at the moment.")
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
          }
        }
      }.recover(handleExceptions("action"))
    }

  @Deprecated
  private def checkDescriptionExists(
    action: models.Action,
    description: Option[String]
  ) =
    if (action.commentNeeded && !description.isDefined) {
      throw new AdaException("Description not provided or not accepted '" + description + "'" + " for new state: " + action.toState)
    }

  private def getIdUserMap(
    userIds: Traversable[BSONObjectID]
  ) =
    userRepo.find(Seq(UserIdentity.name #-> userIds.map(Some(_)).toSeq)).map { users =>
      users.map(user => (user._id.get, user)).toMap
    }

  private def getNotificationType(action: models.Action, role: Role.Value) =
    action.notified.find(r => r == role) match {
      case Some(_) =>
        NotificationType.Advice

      case None =>
        if (action.solicited == role) NotificationType.Solicitation else NotificationType.Advice
  }

  private def isRequestAllowed(
    requestId: BSONObjectID,
    action: Option[RequestAction.Value] = None,
    assumedRole: Option[Role.Value] = None,
    readOnly: Boolean = false)(
    deadboltUser: DeadboltUser,
    request: AuthenticatedRequest[Any]
  ): Future[Boolean] =
    for {
      requestOption <- repo.get(requestId)

      request = requireDefined(requestOption, s"Batch order request '${requestId}' not found.")

      allowedRoles = determineValidRoles(action, assumedRole, readOnly, request)

      userIdsMapping <- batchOrderService.getAllowedUserIds(request)
    } yield {

      val assumedRoleHasPermission = userIdsMapping.find(entry =>
        allowedRoles.contains(entry._1) && entry._2.toSeq.contains(deadboltUser.id.get)
      ).isDefined

      val assumedRoleMatchesUserRole = assumedRole match {
        case Some(role) =>
          userIdsMapping.get(role).get.toSeq.contains(deadboltUser.id.get)

        case None => true
      }
      assumedRoleHasPermission && assumedRoleMatchesUserRole
    }

  private def determineValidRoles(
    action: Option[RequestAction.Value],
    assumedRole: Option[Role.Value],
    readOnly: Boolean,
    existingRequest: BatchOrderRequest
  ): Set[Role.Value] =
    if (!readOnly) {
      action match {
        case Some(action) =>
          assumedRole match {
            case Some(role) =>
              if (getNextState(existingRequest.state, action).allowed == role) Set(role) else Set()

            case None => throw new AdaException("No role existing with action request " + action)
          }

        case None => ActionGraph.asMap.get(existingRequest.state).get.map(a => a.allowed).toSet
      }
    } else {
      Set(Role.Requester, Role.Owner, Role.Committee)
    }

  private def getNextState(
    currentState: BatchRequestState.Value,
    action: RequestAction.Value
  ): models.Action =
    ActionGraph.asMap.get(currentState).get.find(validAction => validAction.action == action) match {
      case Some(allowedAction) => allowedAction
      case None => throw new AdaException("Action '" + action + "' not allowed for current state '" + currentState + "'")
    }

  private def requireDefined[T](
    value: Option[T],
    message: String
  ) =
    value.getOrElse(throw new AdaException(message))
}