package controllers

import play.twirl.api.Html

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._

import play.api.mvc.{Action, Controller}
import play.api.data.Forms._
import play.api.data._

// authentification
import jp.t2v.lab.play2.auth.LoginLogout
import models.security.{CustomUser, UserManager}


class AuthController extends Controller with LoginLogout with AuthConfigImpl{

  /**
    * Login form defintion.
    */
  val loginForm = Form {
    mapping("email" -> email, "password" -> text)(UserManager.authenticate)(_.map(u => (u.email, "")))
      .verifying("Invalid email or password", result => result.isDefined)
  }

  /**
    * Redirect to login page.
    */
  def login = Action { implicit request =>
    /*if()
      Ok(views.html.auth.login(loginForm))
    else
      Redirect(routes.AppController.index)*/
    Ok(views.html.auth.login(loginForm))
  }

  /**
    * Remember log out state and redirect to main page.
    */
  def logout = Action.async { implicit request =>
    gotoLogoutSucceeded.map(_.flashing(
      "success" -> "Logged out"
    ).removingFromSession("rememberme"))
  }

  /**
    * Redirect to logout message page
    */
  def loggedOut = Action { implicit request =>
    Ok(views.html.auth.loggedOut())
  }

  /**
    * Check user name and password.
    *
    * @return Redirect to success page (if successful) or redirect back to login form (if failed).
    */
  def authenticate = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.login(formWithErrors))),
      user => gotoLoginSucceeded(user.get.getIdentifier)
    )
  }

  /**
    * TODO: Give more specific error message (e.g. you are supposed to be admin)
    * Redirect user on authorization failure.
    */
  def unauthorized = Action { implicit request =>
    val message = "It appears that you don't have sufficient rights for access. Please login to proceed."
    Ok(views.html.auth.login(loginForm, Some(message)))
  }


  // TODO: debug login. remove later!
  // immediately login as basic user
  def loginBasic = Action.async{ implicit request =>
    gotoLoginSucceeded(UserManager.basicUser.getIdentifier)
  }

  // TODO: debug login. remove later!
  // immediately login as admin user
  def loginAdmin = Action.async{ implicit request =>
    gotoLoginSucceeded(UserManager.adminUser.getIdentifier)
  }

}