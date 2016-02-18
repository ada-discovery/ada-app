package controllers

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

import play.api.mvc.{Action, Controller}
import play.api.data.Forms._
import play.api.data._

// authentification
import jp.t2v.lab.play2.auth.LoginLogout
import models.security.Account


class AuthController extends Controller with LoginLogout with AuthConfigImpl{

  ///
  /// play20-auth
  ///
  /**
    * Login form defintion.
    */
  val loginForm = Form {
    mapping("email" -> email, "password" -> text)(Account.authenticate)(_.map(u => (u.email, "")))
      .verifying("Invalid email or password", result => result.isDefined)
  }

  /**
    * Redirect to login page.
    *
    */
  def login = Action { implicit request =>
    Ok(views.html.auth.login(loginForm))
  }

  /**
    * Remember log out state and redirect to main page.
    *
    * @return Redirect to main page.
    */
  def logout = Action.async { implicit request =>
    gotoLogoutSucceeded.map(_.flashing(
      "success" -> "Logged out"
    ).removingFromSession("rememberme"))
  }

  /**
    * Check user name and password.
    *
    * @return Redirect to success page (if successful) or redirect back to login form (if failed).
    */
  def authenticate = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.login(formWithErrors))),
      user => gotoLoginSucceeded(user.get.id)
    )
  }


  // TODO: debug login. remove later!
  // immediately login as default user
  def loginUser = Action.async{ implicit request =>
    gotoLoginSucceeded(Account.accountBasic.id)
  }

  // TODO: debug login. remove later!
  // immediately login as admin user
  def loginAdmin = Action.async{ implicit request =>
    gotoLoginSucceeded(Account.accountAdmin.id)
  }

}