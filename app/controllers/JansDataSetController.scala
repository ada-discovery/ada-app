package controllers

import javax.inject.Inject
import play.api.mvc.{Action, Controller}

import be.objectify.deadbolt.scala.DeadboltActions

/**
 * Class for testing and debugging
 */
class JansDataSetController @Inject()(
  deadbolt: DeadboltActions) extends Controller{

  // debug: show session
  def showSession = Action { implicit request =>
    Ok(request.session.toString)
  }
  // debug: clear session
  def clearSession = Action { implicit request =>
    Ok("sessions cleared").withNewSession
  }
}