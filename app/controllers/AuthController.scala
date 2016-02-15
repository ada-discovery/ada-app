package controllers

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

import play.api.mvc.{Action, Controller}
import play.api.data.Forms._
import play.api.data._

// authentificatio
import jp.t2v.lab.play2.auth.LoginLogout
import models.security.Account



class AuthController extends Controller with LoginLogout with AuthConfigImpl{

  def index = Action { implicit request =>
    Redirect(routes.AuthController.login())
  }

  ///
  /// play20-auth
  ///
  val loginForm = Form {
    mapping("email" -> email, "password" -> text)(Account.authenticate)(_.map(u => (u.email, "")))
      .verifying("Invalid email or password", result => result.isDefined)
  }

  def login = Action { implicit request =>
    Ok(views.html.auth.login(loginForm))
  }

  def logout = Action.async { implicit request =>
    // do something...
    // remember log out state (e.g. message)
    gotoLogoutSucceeded.map(_.flashing(
      "success" -> "You've been logged out"
    ).removingFromSession("rememberme"))
  }

  def authenticate = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.login(formWithErrors))),
      user => gotoLoginSucceeded(user.get.id)
    )
  }

  // TODO: debug login. remove later!
  // immediately login as default user
  def loginUser = Action.async{ implicit request =>
    gotoLoginSucceeded(Account.accountNormal.id)
  }

  // TODO: debug login. remove later!
  // immediately login as admin user
  def loginAdmin = Action.async{ implicit request =>
    gotoLoginSucceeded(Account.accountAdmin.id)
  }

}