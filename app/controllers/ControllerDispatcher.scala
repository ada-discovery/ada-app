package controllers

import play.api.mvc.{Action, AnyContent, Controller, Request}

/**
 * Simple dispatcher using controller id lookup to find a corresponding controller to redirect a call (function) to
 */
abstract class ControllerDispatcher[C](controllerParamId: String, controllers : Iterable[(String, C)]) extends Controller {

  private val idControllerMap = controllers.toMap

  protected def dispatch(action: C => Action[AnyContent]) = Action.async { implicit request =>
    val controllerId = getControllerId(request)

    val matchingController = idControllerMap.getOrElse(
      controllerId,
      throw new IllegalArgumentException(s"Controller id '${controllerId}' not recognized.")
    )

    action(matchingController).apply(request)
  }

  // a helper function
  protected def getControllerId(request: Request[AnyContent]) = {
    val controllerIdOption = request.queryString.get(controllerParamId)
    controllerIdOption.getOrElse(
      throw new IllegalArgumentException(s"Controller param id '${controllerParamId}' not found.")
    ).head
  }
}