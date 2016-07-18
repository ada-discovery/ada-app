package controllers

import javax.inject.Inject

import be.objectify.deadbolt.core.PatternType
import be.objectify.deadbolt.scala.DeadboltActions
import be.objectify.deadbolt.scala.cache.HandlerCache
import models.security.SecurityRole
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.{Action, AnyContent, Result}
import play.api.routing.Router.Tags._
import security.HandlerKeys
import util.SecurityUtil
import collection.mutable.{Map => MMap}

import scala.concurrent.Future
import play.api.http.{Status => HttpStatus}

abstract class SecureControllerDispatcher[C](controllerParamId: String) extends ControllerDispatcher[C](controllerParamId) {

  @Inject protected var deadbolt: DeadboltActions = _
  @Inject protected var deadboltHandlerCache: HandlerCache = _

  private val actionNameMap = MMap[(String, String), (C => Action[AnyContent]) => Action[AnyContent]]()

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

  private def createRestrictedAction(
    controllerId: String,
    actionName: String)(
    action: C => Action[AnyContent]
  ): Action[AnyContent] = {
    val roleGroups = getAllowedRoleGroups(controllerId, actionName)
    val permission = getPermission(controllerId, actionName)

    // TODO: once we migrate to deadbolt >= 2.5 we can use deadbolt.Composite instead

    val actionTransform: Action[AnyContent] => Action[AnyContent] =
      if (roleGroups.nonEmpty && permission.isDefined)
        RestrictOrPattern[AnyContent](roleGroups, permission.get)_
      else if (roleGroups.nonEmpty)
        deadbolt.Restrict[AnyContent](roleGroups)_
      else if (permission.isDefined)
        deadbolt.Pattern[AnyContent](permission.get, PatternType.REGEX)_
      else
        // no deadbolt action needed
        identity[Action[AnyContent]]_

    actionTransform(super.dispatch(action))
  }

  protected def dispatchx(action: C => Action[AnyContent]) = Action.async { implicit request =>
    val controllerId = getControllerId(request)
//    println(request.tags.get(RouteController).get)
    val actionName = request.tags.get(RouteActionMethod).get
    val roleGroups = getAllowedRoleGroups(controllerId, actionName)
    val permission = getPermission(controllerId, actionName)

    val actionTransform: Action[AnyContent] => Action[AnyContent] =
      if (roleGroups.nonEmpty && permission.isDefined)
        RestrictOrPattern[AnyContent](roleGroups, permission.get)_
      else if (roleGroups.nonEmpty)
        deadbolt.Restrict[AnyContent](roleGroups)_
      else if (permission.isDefined)
        deadbolt.Pattern[AnyContent](permission.get, PatternType.REGEX)_
      else
      // no deadbolt action needed
        identity[Action[AnyContent]]_

    actionTransform(super.dispatch(action)).apply(request)
  }

  private def RestrictOrPattern[A](roleGroups: List[Array[String]], permission: String)(action: Action[A]): Action[A] = {
    Action.async(action.parser) { implicit request =>
      val resultFuture: Future[Result] =
        deadbolt.Restrict[A](roleGroups, deadboltHandlerCache.apply(HandlerKeys.unauthorizedStatus))(action)(request)
      resultFuture.flatMap(result =>
        if (result.header.status == HttpStatus.UNAUTHORIZED)
          deadbolt.Pattern[A](permission, PatternType.REGEX)(action)(request)
        else
          Future(result)
      )
    }
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