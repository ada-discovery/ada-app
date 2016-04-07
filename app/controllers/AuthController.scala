package controllers

import javax.inject.Inject

import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.{JsNull, JsString, JsObject}
import play.api.mvc.{Action, Controller}
import play.api.data.Forms._
import play.api.data._
import play.api.libs.mailer._
import play.api.{Configuration, Logger}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

// authentification
import jp.t2v.lab.play2.auth.LoginLogout
import models.security._
import security.AdaAuthConfig
import util.SecurityUtil



class AuthController @Inject() (
    myUserManager: UserManager,
    configuration: Configuration
  ) extends Controller with LoginLogout with AdaAuthConfig {

  // a hook need by auth config
  override val userManager = myUserManager

  // define custom mailer
  def mailer: MailerClient = new SMTPMailer(
    new SMTPConfiguration(
      host = configuration.getString("play.mailer.host").getOrElse("smtp-1.uni.lu"),
      port = configuration.getInt("play.mailer.port").getOrElse(587),
      ssl = configuration.getBoolean("play.mailer.ssl").getOrElse(false),
      tls = configuration.getBoolean("play.mailer.tls").getOrElse(false),
      user = configuration.getString("play.mailer.user"),
      password = configuration.getString("play.mailer.password"),
      debugMode = configuration.getBoolean("play.mailer.host").getOrElse(false),
      timeout = configuration.getInt("play.mailer.host"),
      connectionTimeout = configuration.getInt("play.mailer.host"),
      mock = configuration.getBoolean("play.mailer.host").getOrElse(true)
    )
  )

  /**
    * Login form definition.
    */
  val loginForm = Form {
    tuple(
      "email" -> email,
      "password" -> text
    ).verifying(
      "Invalid email or password",
      emailPassword => Await.result(userManager.authenticate(emailPassword._1, emailPassword._2), 120000 millis)
    )
  }

  /**
    * Form for resetting password.
    */
  val recoveryForm = Form {
    single(
      "email" -> email
    ).verifying(
      "Invalid or unknown email",
      email => Await.result(userManager.findByEmail(email), 120000 millis).isDefined
    )
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
    * Redirect to logout message page
    */
  def loggedOut = Action { implicit request =>
    Ok(views.html.auth.loggedOut())
  }

  /**
    * Login for restful api.
    * Gives restful response for form errors and login success.
    * @return
    */
  def loginREST = Action.async { implicit request =>
    loginForm.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(loginForm.errorsAsJson)),
      emailPassword => {
        val usrOpFuture: Future[Option[CustomUser]] = myUserManager.findByEmail(emailPassword._1)
        usrOpFuture.flatMap{usrOp =>
          val usrJs = JsObject("user" -> JsString(usrOp.get.name) :: Nil)
          gotoLoginSucceeded(usrOp.get.name, Future.successful((Ok(usrJs))))}
      }
    )
  }

  /**
    * Logout for restful api.
    * @return
    */
  def logoutREST = Action { implicit request =>
    tokenAccessor.delete(Ok(JsNull))
  }

  /**
    * Check user name and password.
    *
    * @return Redirect to success page (if successful) or redirect back to login form (if failed).
    */
  def authenticate = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.login(formWithErrors))),
      emailPassword => myUserManager.findByEmail(emailPassword._1).flatMap(user => gotoLoginSucceeded(user.get.name))
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


  /**
    * Leads to page for password recovery.
    */
  def passwordRecovery() = Action{ implicit reqeust =>
    Ok(views.html.auth.recoverPassword(recoveryForm))
  }

  /**
    *
    * @return
    */
  def resetPassword = Action.async { implicit request =>
    recoveryForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.recoverPassword(formWithErrors))),
      emailDst => {
        val newPassword: String = SecurityUtil.randomString(9)
        val userOpFuture: Future[Option[CustomUser]] = userManager.findByEmail(emailDst)
        userOpFuture.map{ userOp: Option[CustomUser] =>
          val user: CustomUser = userOp.get
          val mail = Email(
            "[Ada Reporting System] Password Reset",
            "Jan MARTENS FROM <jan.martens@uni.lu>",
            Seq(emailDst),
            attachments = Seq(),
            bodyText = Some("This message has been sent to you due to a password reset request to the Ada Reporting System." + System.lineSeparator() +
                            "If you did not request the password change, ignore this mail." + System.lineSeparator() +
                            "Your newly generated password is: " + newPassword + System.lineSeparator() +
                            "Use this password to login at Ada's login page."),
            bodyHtml = None
          )
          mailer.send(mail)
          Logger.info("Password reset for user [" + emailDst + "] requested")
          userManager.updateUser(CustomUser(user._id, user.name, user.email, SecurityUtil.md5(newPassword), user.affiliation, user.roles, user.permissions))
          Ok(views.html.auth.passwordChange(emailDst))
        }
      }
    )
  }

  // TODO: debug login. remove later!
  // immediately login as basic user
  def loginBasic = Action.async{ implicit request =>
    gotoLoginSucceeded(userManager.basicUser.getIdentifier)
  }

  // TODO: debug login. remove later!
  // immediately login as admin user
  def loginAdmin = Action.async{ implicit request =>
    gotoLoginSucceeded(userManager.adminUser.getIdentifier)
  }
}