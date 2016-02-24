package controllers

import javax.inject.Inject
import play.api.mvc.{Action, Controller}

import models.security.{AbstractUser, SecurityRoleCache, UserManager}

import jp.t2v.lab.play2.auth.AuthElement
import be.objectify.deadbolt.scala.DeadboltActions

/**
 * Class for testing and debugging
 */
class JansDataSetController @Inject() (
    deadbolt: DeadboltActions
  ) extends Controller with AuthElement with AuthConfigImpl{

  // deadbolt tests
  def restrictedCall = deadbolt.Restrict(List(Array(SecurityRoleCache.adminRole.getName))) {
    Action{implicit request =>
      Ok("this call is only seen, if subject matches restrictions")
    }
  }

  // debug: show session
  def showSession = Action { implicit request =>
    Ok(request.session.toString)
  }
  // debug: clear session
  def clearSession = Action { implicit request =>
    Ok("sessions cleared").withNewSession
  }

}