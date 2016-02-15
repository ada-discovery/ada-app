package controllers

import jp.t2v.lab.play2.auth.{AuthConfig, _}

import models.security.Role.{NormalUser, Administrator}
import models.security.{Account, Role}

import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._


/**
  *
  *
  * TODO clean
  * TODO add redirects to meaningful pages
  * TODO add logic
  *
  * used by auth controller and messages controller module
  *
  */
trait AuthConfigImpl extends AuthConfig {


  /**
    * A type that is used to identify a user.
    * `String`, `Int`, `Long` and so on.
    */
  type Id = Int

  /**
    * A type that represents a user in your application.
    * `User`, `Account` and so on.
    */
  type User = Account

  /**
    * A type that is defined by every action for authorization.
    * This sample uses the following trait:
    *
    * sealed trait Role
    * case object Administrator extends Role
    * case object NormalUser extends Role
    */
  type Authority = Role

  /**
    * A `ClassTag` is used to retrieve an id from the Cache API.
    * Use something like this:
    */
  val idTag: ClassTag[Id] = classTag[Id]

  /**
    * The session timeout in seconds
    */
  val sessionTimeoutInSeconds: Int = 3600

  /**
    * A function that returns a `User` object from an `Id`.
    * You can alter the procedure to suit your application.
    */
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] = {
    //Future(None)
    Future.successful(Account.findById(id))
  }

  /**
    * Where to redirect the user after a successful login.
    * TODO: redirect to different page
    */
  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    //Future.successful(Redirect(routes.AppController.index()))
    Future.successful(Ok("login succeeded"))
  }

  /**
    * Where to redirect the user after logging out
    */
  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    //Future.successful((Ok("successfully logged out")))
    Future.successful(Redirect(routes.AppController.index))
  }

  /**
    * If the user is not logged in and tries to access a protected resource, redirect to login page
    */
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    // Some("Please log in to proceed")
    Future.successful(Redirect(routes.AuthController.login))
  }


  /**
    * If authorization failed (usually incorrect password) redirect the user as follows:
    */
  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Forbidden(routes.AuthController.login))
  }

  /**
    * TODO: exchange by deadbolt authorization
    *
    * A function that determines what `Authority` a user has.
    * You should alter this procedure to suit your application.
    *
    */
  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = Future.successful {
    (user.role, authority) match {
      case (Administrator, _)       => true
      case (NormalUser, NormalUser) => true
      case _                        => false
    }
  }

  /**
    * (Optional)
    * You can use custom SessionID Token handler.
    * Default implementation uses Cookie.
    */
  override lazy val tokenAccessor = new CookieTokenAccessor(
    /*
     * Whether use the secure option or not use it in the cookie.
     * Following code is default.
     */
    //cookieSecureOption = !play.api.Play.isProd(play.api.Play.current),
    cookieSecureOption = play.api.Play.isProd(play.api.Play.current),
    cookieMaxAge       = Some(sessionTimeoutInSeconds)
  )

}