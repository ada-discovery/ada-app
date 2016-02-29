package controllers

import javax.inject.Inject
import play.api.mvc.{Action, Controller}

import models.security.SecurityRoleCache

import be.objectify.deadbolt.scala.DeadboltActions

import modules.LdapModule
import ldap._



/**
 * Class for testing and debugging
 */
class JansDataSetController @Inject() (
    deadbolt: LdapActions
  ) extends Controller{

  // deadbolt tests
  def restrictedCall = deadbolt.Restrict(List(Array(SecurityRoleCache.adminRole))) {
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