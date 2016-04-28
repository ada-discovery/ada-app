package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import models.security.SecurityRoleCache
import play.api.mvc.{Action, AnyContent}
import play.api.routing.Router.Tags._

abstract class SecureControllerDispatcher[C](controllerParamId: String)
  extends ControllerDispatcher[C](controllerParamId) {

  @Inject protected var deadbolt: DeadboltActions = _
  protected def getAllowedRoleGroups(controllerId: String, actionName: String): List[Array[String]]

  override protected def dispatch(action: C => Action[AnyContent]) = Action.async { implicit request =>
    val controllerId = getControllerId(request)

//    println(request.tags.get(RouteController).get)

    val actionName = request.tags.get(RouteActionMethod).get
    val roleGroups = getAllowedRoleGroups(controllerId, actionName)

    deadbolt.Restrict(roleGroups)(super.dispatch(action)).apply(request)
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