package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import be.objectify.deadbolt.scala.cache.HandlerCache
import be.objectify.deadbolt.scala.models.PatternType
import dataaccess.User
import models.AdaException
import models.security.SecurityRole

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc._
import play.api.routing.Router.Tags._
import security.{AdaAuthConfig, HandlerKeys}
import util.SecurityUtil

import collection.mutable.{Map => MMap}
import scala.concurrent.Future
import play.api.http.{Status => HttpStatus}
import be.objectify.deadbolt.scala.AuthenticatedRequest
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil.{AuthenticatedAction, restrictChainFuture, restrictChainFuture2, toActionAny}

abstract class SecureControllerDispatcher[C](controllerParamId: String) extends ControllerDispatcher[C](controllerParamId) {

  @Inject protected var deadbolt: DeadboltActions = _
  @Inject protected var deadboltHandlerCache: HandlerCache = _

  protected val actionNameMap = MMap[(String, String), (C => Action[AnyContent]) => Action[AnyContent]]()

  protected def getAllowedRoleGroups(
    controllerId:
    String, actionName: String
  ): List[Array[String]] = List()

  protected def getPermission(
    controllerId: String,
    actionName: String
  ): Option[String] = None

  override protected def dispatch(action: C => Action[AnyContent]) = Action.async { implicit request =>
    val controllerId = getControllerId(request)
    val actionName = request.tags.get(RouteActionMethod).get

    val restrictedAction = actionNameMap.getOrElseUpdate(
      (controllerId, actionName),
      createRestrictedAction(controllerId, actionName)_
    )
    restrictedAction(action).apply(request)
  }

  protected def createRestrictedAction(
    controllerId: String,
    actionName: String)(
    action: C => Action[AnyContent]
  ): Action[AnyContent] = {
    val roleGroups = getAllowedRoleGroups(controllerId, actionName)
    val permission = getPermission(controllerId, actionName)

    // TODO: once we migrate to deadbolt >= 2.5 we can use deadbolt.Composite instead

    val actionTransform: AuthenticatedAction[AnyContent] => Action[AnyContent] =
      if (roleGroups.nonEmpty && permission.isDefined)
        RestrictOrPattern(roleGroups, permission.get)_
      else if (roleGroups.nonEmpty)
        deadbolt.Restrict[AnyContent](roleGroups)()_
      else if (permission.isDefined)
        deadbolt.Pattern[AnyContent](permission.get, PatternType.REGEX)()_
      else
        // no deadbolt action needed
        {authenticatedAction => toActionAny(authenticatedAction)} //  identity[Action[AnyContent]]_

    actionTransform(dispatchAuthenticated(action))
  }

  private def dispatchAuthenticated(
    action: C => Action[AnyContent]
  ): AuthenticatedAction[AnyContent] =
    SecurityUtil.toAuthenticatedAction(super.dispatch(action))

  private def RestrictOrPattern(
    roleGroups: List[Array[String]],
    permission: String)(
    action: AuthenticatedAction[AnyContent]
  ): Action[AnyContent] =
    SecurityUtil.restrictChain2(Seq(
      deadbolt.Restrict[AnyContent](roleGroups, unauthorizedDeadboltHandler)()_,
      deadbolt.Pattern[AnyContent](permission, PatternType.REGEX)()_
    ))(action)

  //  private def RestrictOrPattern[A](
//    roleGroups: List[Array[String]],
//    permission: String)(
//    action: AuthenticatedAction[A]
//  ): Action[A] =
//    SecurityUtil.restrictChain[A](Seq(
//      deadbolt.Restrict[A](roleGroups, unauthorizedDeadboltHandler)()_,
//      deadbolt.Pattern[A](permission, PatternType.REGEX)()_
//    ))(action)

  protected def unauthorizedDeadboltHandler =
    deadboltHandlerCache.apply(HandlerKeys.unauthorizedStatus)

  protected def defaultDeadboltHandler =
    deadboltHandlerCache.apply(HandlerKeys.default)

  protected def dispatchIsAdminOrOwnerAux(
    objectOwnerId: Request[AnyContent] => Future[Option[BSONObjectID]],
    currentUser: Request[_] => Future[Option[User]])(
    action: C => Action[AnyContent]
  ) = Action.async { implicit request =>
    val originalAction = dispatchAuthenticated(action)

    // check if the view owner matches a currently logged user
    def checkOwner = { action: AuthenticatedAction[AnyContent] =>
      val unauthorizedAction: Action[AnyContent] =
        toActionAny{ implicit req: AuthenticatedRequest[AnyContent] => defaultDeadboltHandler.onAuthFailure(req) }

      val accessingUserFuture = currentUser(request)
      val objectOwnerIdFuture = objectOwnerId((request))

      for {
        objectOwnerId <- objectOwnerIdFuture
        accessingUser <- accessingUserFuture
      } yield {
        objectOwnerId match {
          case Some(createdById) =>
            accessingUser.map { accessingUser =>
              // if the user accessing the data view is the owner process, otherwise "unauthorized"
              if (accessingUser._id.get.equals(createdById)) toActionAny(action) else unauthorizedAction
            }.getOrElse(
              // if we cannot determine the currently logged user for some reason return "unauthorized"
              unauthorizedAction
            )
          case None => unauthorizedAction
        }
      }
    }

    // is admin?
    def isAdmin = { action: AuthenticatedAction[AnyContent] =>
      Future(
        deadbolt.Restrict[AnyContent](List(Array(SecurityRole.admin)), unauthorizedDeadboltHandler)()(action)
      )
    }

    val extraRestrictions = restrictChainFuture2(Seq(isAdmin, checkOwner))_
    extraRestrictions(originalAction)(request)
  }

  protected def dispatchIsAdminAux(
    currentUser: Request[_] => Future[Option[User]])(
    action: C => Action[AnyContent]
  ) = Action.async { implicit request =>
    val originalAction = dispatchAuthenticated(action)

    // is admin?
    def isAdmin = { action: AuthenticatedAction[AnyContent] =>
      Future(
        deadbolt.Restrict[AnyContent](List(Array(SecurityRole.admin)), unauthorizedDeadboltHandler)()(action)
      )
    }

    val extraRestrictions = restrictChainFuture2(Seq(isAdmin))_
    extraRestrictions(originalAction)(request)
  }
}

abstract class StaticSecureControllerDispatcher[C](controllerParamId: String, controllers : Iterable[(String, C)]) extends SecureControllerDispatcher[C](controllerParamId) {

  private val idControllerMap = controllers.toMap

  override protected def getController(controllerId: String): C =
    idControllerMap.getOrElse(
      controllerId,
      throw new IllegalArgumentException(s"Controller id '${controllerId}' not recognized.")
    )
}