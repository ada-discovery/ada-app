package controllers

import jp.t2v.lab.play2.auth.{AuthConfig, _}

import models.security.{Account, SecurityRole}

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
  type Authority = SecurityRole

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
    * Retrieves user from Account class.
    *
    */
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] = {
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
    Future.successful(Redirect(routes.AppController.index))
  }

  /**
    * If the user is not logged in and tries to access a protected resource, redirect to login page
    */
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(routes.AuthController.login))
  }


  /**
    * TODO: exchange with deadbolt authorization
    * If authorization failed (usually incorrect password) redirect the user as follows:
    * Show error message
    */
  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Forbidden("Not authorised. Please change user or login to proceed"))
  }

  /**
    * TODO: exchange with deadbolt authorization
    *       this is only used, if play2-auth authorization is required
    *
    * Maps users to permissions
    *
    */
  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = Future.successful {
    (user.role, authority) match {
      case (accountAdmin, _)            => true
      case (accountNormal, roleNormal)  => true
      case _                            => false
    }
  }

  /**
    * (Optional)
    * You can custom SessionID Token handler.
    * Default implementation use Cookie.
    */
  override lazy val tokenAccessor = new CookieTokenAccessor(
    cookieSecureOption = play.api.Play.isProd(play.api.Play.current),
    cookieMaxAge       = Some(sessionTimeoutInSeconds)
  )

}