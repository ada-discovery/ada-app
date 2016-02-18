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
  def restrictedCall = deadbolt.Restrict(List(Array(""))) {
    Action{implicit request =>
      Ok("this call is only seen, if subject matches restrictions")
    }
  }
  def notpresent = deadbolt.SubjectNotPresent(){
    Action{
      Ok("deadbolt does not see a subject")
    }
  }
  // only visible if logged in
  def present = deadbolt.SubjectPresent(){
    Action{
      Ok("deadbolt sees a present subject!")
    }
  }

  // substitute stackaction with deadbolt action
  def currentUser = StackAction(AuthorityKey -> SecurityRoleCache.basicRole) { implicit request =>
    val user: AbstractUser = loggedIn
    Ok("logged in as: " + user.getMail)
  }

  def resolve = Action { implicit request =>
    //Account.findById()
    //val usr = resolveUser()
    Ok("")
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