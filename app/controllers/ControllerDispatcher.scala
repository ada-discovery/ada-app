package controllers

import play.api.mvc.{Action, AnyContent, Controller, Request}

/**
 * Simple dispatcher using controller id lookup to find a corresponding controller to redirect a call (function) to
 */
abstract class ControllerDispatcher[C](controllerParamId: String, controllers : Iterable[(String, C)]) extends Controller {

  val controllerIdMap = controllers.toMap

  protected def dispatch(action: C => Action[AnyContent]) = Action.async { implicit request =>
    // helper function to find a matching controller
    val matchingController: C = {
      val controllerIdOption = request.queryString.get(controllerParamId)
      val controllerId = controllerIdOption.getOrElse(
        throw new IllegalArgumentException(s"Controller param id '${controllerParamId}' not found.")
      ).head
      controllerIdMap.getOrElse(controllerId, throw new IllegalArgumentException(s"Controller id '${controllerId}' not recognized."))
    }

    action(matchingController).apply(request)
  }
}