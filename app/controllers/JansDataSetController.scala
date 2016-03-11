package controllers

import java.io.Serializable
import javax.inject.Inject
import jp.t2v.lab.play2.auth.AuthElement
import play.api.mvc.{Action, Controller}

import models.security.{CustomUser, UserManager, SecurityRoleCache}

import be.objectify.deadbolt.scala.DeadboltActions

import ldap._
import security.AdaAuthConfig


import scala.concurrent.ExecutionContext.Implicits.global


/**
 * Class for testing and debugging
 */
class JansDataSetController @Inject() (
    myUserManager: UserManager,
    //myLdapUserManager: LdapUserManager,
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

  /*def listLDAP = Action { implicit request =>
    val users: List[String] = myLdapUserManager.getEntryList
    val content = "ldap entry list (" + users.size + "):\n" + users.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }*/

}