package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import play.api.mvc.{Action, AnyContent}

abstract class SecureControllerDispatcher[C](controllerParamId: String, controllers: Iterable[(String, C, Array[String])])
  extends StaticControllerDispatcher[C](controllerParamId, controllers.map(x => (x._1, x._2))) {

  @Inject protected var deadbolt: DeadboltActions = _
  private val idRolesMap = controllers.map(x => (x._1, x._3)).toMap

  override protected def dispatch(action: C => Action[AnyContent]) = Action.async { implicit request =>
    val controllerId = getControllerId(request)
    val roles = idRolesMap.get(controllerId).get

    deadbolt.Restrict(List(roles))(super.dispatch(action)).apply(request)
  }
}