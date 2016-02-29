package controllers

import javax.inject.Inject
import jp.t2v.lab.play2.auth.AuthElement
import play.api.mvc.{Action, Controller}

import models.security.{UserManager, SecurityRoleCache}

import be.objectify.deadbolt.scala.DeadboltActions

import modules.LdapModule
import ldap._
import security.AdaAuthConfig


/**
 * Class for testing and debugging
 */
class JansDataSetController @Inject() (
    myUserManager: UserManager,
    deadbolt: LdapActions
  ) extends Controller with AuthElement with AdaAuthConfig {

  // a hook need by auth config
  override val userManager = myUserManager

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