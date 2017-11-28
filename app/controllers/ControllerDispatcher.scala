package controllers

import play.api.mvc.{Action, AnyContent, Controller, Request}
import util.SecurityUtil
import util.SecurityUtil.AuthenticatedAction

/**
 * Simple dispatcher using controller id lookup to find a corresponding controller to redirect a call (function) to
 */
abstract class ControllerDispatcher[C](controllerParamId: String) extends Controller {

  protected def getController(controllerId: String): C

  protected def dispatch(
    action: C => Action[AnyContent]
  ): Action[AnyContent] =
    Action.async { implicit request =>
      val controllerId = getControllerId(request)
      action(getController(controllerId)).apply(request)
    }

  // a helper function
  protected def getControllerId(request: Request[AnyContent]) = {
    val controllerIdOption = request.queryString.get(controllerParamId)
    controllerIdOption.getOrElse(
      throw new IllegalArgumentException(s"Controller param id '${controllerParamId}' not found.")
    ).head
  }
}

class StaticControllerDispatcher[C](controllerParamId: String, controllers : Iterable[(String, C)]) extends ControllerDispatcher[C](controllerParamId) {

  private val idControllerMap = controllers.toMap

  override protected def getController(controllerId: String): C =
    idControllerMap.getOrElse(
      controllerId,
      throw new IllegalArgumentException(s"Controller id '${controllerId}' not recognized.")
    )
}