package controllers

import javax.inject.Inject

import jp.t2v.lab.play2.auth.LoginLogout
import play.api.data.Forms._
import play.api.data._

import play.api.mvc.{Results, Action, Controller}


// authentification, authorisation
import be.objectify.deadbolt.scala.DeadboltActions

import jp.t2v.lab.play2.auth.sample.{Role, Account}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits._



class AuthController @Inject()(
    deadbolt: DeadboltActions
  ) extends Controller with LoginLogout with AuthConfigImpl{

  // deadbolt tests
  def restrictedCall = deadbolt.Restrict(List(Array(""))) {
    Action{implicit request =>
        Ok("you are in")
    }
  }
  def notpresent = deadbolt.SubjectNotPresent(){
    Action{
      Ok("subject not present, but everything is fine!")
    }
  }
  def present = deadbolt.SubjectPresent(){
    Action{
      Ok("there you are, present subject!")
    }
  }



  ///
  /// play20-auth
  ///
  val loginForm = Form {
    mapping("email" -> email, "password" -> text)(Account.authenticate)(_.map(u => (u.email, "")))
      .verifying("Invalid email or password", result => result.isDefined)

  }

  /** Alter the login page action to suit your application. */
  def login = Action { implicit request =>
    Ok(views.html.auth.login(loginForm))
    //Ok("login screen here")
  }

  def logout = Action.async { implicit request =>
    // do something...
    // remember log out state (e.g. message)
    gotoLogoutSucceeded.map(_.flashing(
      "success" -> "You've been logged out"
    ).removingFromSession("rememberme"))
  }

  /*def authenticate = Action.async { implicit request =>
    // dummy
    gotoLogoutSucceeded
  }*/

  def authenticate = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.login(formWithErrors))),
      user => gotoLoginSucceeded(user.get.id)
    )
  }





// debug logins
def loginUser = Action{
  //gotoLoginSucceeded(Account.accountNormal.id)
  Ok("now logged in as regular user")
}

def loginAdmin = Action{
  //gotoLoginSucceeded(Account.accountAdmin.id)
  Ok("now logged in as admin user")
}












///
/// play-authenticate
///
/*def onUnauthorized(request: RequestHeader): Result = Results.Redirect(routes.AuthController.login())

def doLogin = Action { implicit request =>
  /*loginForm.bindFromRequest.fold(
    formWithErrors => BadRequest(views.html.login(formWithErrors, routes.AuthController.login)),
    user => Redirect(routes.AppController.index).withSession("user" -> user._1)
  )*/
  Ok("log")
}

def login = Action { implicit request =>
  //Ok(views.html.login(loginForm, routes.AuthController.doLogin))
  Ok("asf")
  //Ok(views.html.auth.login(doLogin))
}

def logout = Action {
  //Redirect(routes.AuthController.login).withNewSession.flashing(
  //  "success" -> "You've been logged out"
    Ok("logged out").withNewSession
  //)
}

def authDenied(providerKey: String): Unit =
{
  com.feth.play.module.pa.controllers.Authenticate.noCache("")
}

*/


///
///


def index = Action { implicit request =>
  Redirect(routes.AuthController.login())
}

}