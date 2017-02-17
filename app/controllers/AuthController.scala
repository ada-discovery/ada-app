package controllers

import javax.inject.Inject

import models.FieldTypeId
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.libs.json._
import play.api.mvc.{Action, Controller, Request, Result}
import play.api.data.Forms._
import play.api.data._
import services.MailClientProvider
import views.html.dataset

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

// authentification
import jp.t2v.lab.play2.auth.LoginLogout
import models.security._
import dataaccess.{User => AdaUser}
import security.AdaAuthConfig

class AuthController @Inject() (
    val userManager: UserManager,
    mailClientProvider: MailClientProvider
  ) extends Controller with LoginLogout with AdaAuthConfig {

  /**
    * Login form definition.
    */
  val loginForm = Form {
    tuple(
      "id" -> nonEmptyText,
      "password" -> nonEmptyText
    )
//      .verifying(
//        "Invalid LUMS ID or password",
//        idPassword => Await.result(userManager.authenticate(idPassword._1, idPassword._2), 120000 millis)
//      )
  }

  /**
    * Redirect to login page.
    */
  def login = Action { implicit request =>
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
    * Logout for restful api.
    */
  def logoutREST = Action.async { implicit request =>
    gotoLogoutSucceeded(Future(Ok(s"You have been successfully logged out.\n")))
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
    render.async {
      case Accepts.Html() =>
        authenticateAux(
          (formWithErrors: Form[(String, String)]) => BadRequest(views.html.auth.login(formWithErrors)),
          BadRequest(views.html.auth.login(loginForm.withGlobalError("Invalid user id or password"))),
          Redirect(routes.AuthController.unauthorized()),
          (userId: String) => gotoLoginSucceeded(userId)
        )

      case Accepts.Json() =>
        authenticateAux(
          (formWithErrors: Form[(String, String)]) => BadRequest(formWithErrors.errorsAsJson),
          Unauthorized("Invalid user id or password\n"),
          Unauthorized("User not found.\n"),
          (userId: String) => gotoLoginSucceeded(userId, Future(Ok(s"User '${userId}' successfully logged in. Check the header for a 'PLAY_SESSION' cookie.\n")))
        )
      }
  }

  private def authenticateAux(
    badFormResult: Form[(String, String)] => Result,
    authenticationUnsuccessfulResult: Result,
    userNotFoundResult: Result,
    loginSuccessfulResult: String => Future[Result])(
    implicit request: Request[_]
  ): Future[Result] = {
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(badFormResult(formWithErrors)),
      idPassword =>
        for {
          authenticationSuccessful <- userManager.authenticate(idPassword._1, idPassword._2)
          userOption <- userManager.findById(idPassword._1)
          response <-
            if (!authenticationSuccessful)
              Future(authenticationUnsuccessfulResult)
           else
            userOption match {
              case Some(user) => loginSuccessfulResult(user.ldapDn)
              case None => Future(userNotFoundResult)
            }
        } yield
          response
    )
  }

  /**
    * TODO: Give more specific error message (e.g. you are supposed to be admin)
    * Redirect user on authorization failure.
    */
  def unauthorized = Action { implicit request =>
    val message = "It appears that you don't have sufficient rights for access. Please login to proceed."
    Ok(views.html.auth.login(loginForm)).flashing("errors" -> message)
  }

  // immediately login as basic user
  def loginBasic = Action.async{ implicit request =>
    if(!userManager.debugUsers.isEmpty)
      gotoLoginSucceeded(userManager.basicUser.ldapDn)
    else
      Future(Redirect(routes.AuthController.unauthorized()))
  }

  // immediately login as admin user
  def loginAdmin = Action.async{ implicit request =>
    if(!userManager.debugUsers.isEmpty)
      gotoLoginSucceeded(userManager.adminUser.ldapDn)
    else
      Future(Redirect(routes.AuthController.unauthorized()))
  }
}