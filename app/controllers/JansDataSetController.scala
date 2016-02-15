package controllers

import javax.inject.Inject
import play.api.mvc.{Action, Controller}

import models.security.Account
import models.security.SecurityRole

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
      Ok("you are in")
    }
  }
  def notpresent = deadbolt.SubjectNotPresent(){
    Action{
      Ok("this works if not subject present")
    }
  }
  // only visible if logged in
  def present = deadbolt.SubjectPresent(){
    Action{
      Ok("there you are, present subject!")
    }
  }

  def currentUser = StackAction(AuthorityKey -> new SecurityRole("DefaultUser")) { implicit request =>
    val user: Account = loggedIn
    Ok("logged in as: " + user.email)
  }

  // play2-auth tests
  /*def main = StackAction(AuthorityKey -> DefaultUser) { implicit request =>
    val user: Account = loggedIn
    Ok("logged in as: " + user.name)
  }

  def list = StackAction(AuthorityKey -> NormalUser) { implicit request =>
    val user = loggedIn
    val title = "all messages"
    Ok(title)
  }

  def detail(id: Int) = StackAction(AuthorityKey -> NormalUser) { implicit request =>
    val user = loggedIn
    val title = "messages detail "
    Ok(title + id)
  }

  // Only Administrator can execute this action.
  def write = StackAction(AuthorityKey -> Administrator) { implicit request =>
    val user = loggedIn
    val title: String = "write message"
    Ok(title)
  }*/




  // debug: show session
  def showSession = Action { implicit request =>
    Ok(request.session.toString)
  }
  // debug: clear session
  def clearSession = Action { implicit request =>
    Ok("sessions cleared").withNewSession
  }

}