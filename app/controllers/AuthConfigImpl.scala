package controllers

import jp.t2v.lab.play2.auth.{AuthConfig, _}
import models.security.{UserManager, AbstractUser}
import be.objectify.deadbolt.core.models.Role

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
  * used by auth controller
  *
  */
trait AuthConfigImpl extends AuthConfig {

  /**
    * A type that is used to identify a user.
    * `String`, `Int`, `Long` and so on.
    */
  type Id = String

  /**
    *  Play2-auth specific.
    *  Type defintion for User object.
    *  Set to AbstractUser, a class extending deadbolt's Subject.
    */
  type User = AbstractUser

  /**
    * Play2-auth specific.
    * A type that is defined by every action for authorization.
    * Set to deadbolt's Role class.
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
    * Retrieves user from Account class.
    *
    */
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] = {
    Future.successful(UserManager.findById(id))
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
    //Future.successful(Redirect(routes.AppController.index))
    Future.successful(Redirect(routes.AuthController.loggedOut))
  }

  /**
    * If the user is not logged in and tries to access a protected resource, redirect to login page
    */
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(routes.AuthController.login))
  }

  /**
    * TODO: Exchange with deadbolt authorization.
    *       This is only used, if play2-auth authorization is required.
    *       However, Play2-auth authorization is never used.
    *
    * Shows error message on authorization failure.
    */
  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Forbidden("Not authorised. Please change user or login to proceed"))
  }

  /**
    * TODO: Exchange with deadbolt authorization.
    *       This is only used, if play2-auth authorization is required.
    *       However, Play2-auth authorization is never used.
    *       Always returns true.
    *
    * Maps users to permissions.
    */
  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = Future.successful {
    user.getRoles.contains(authority)
  }

  /**
    * (Optional)
    * You can custom SessionID Token handler.
    * Default implementation use Cookie.
    */
  override lazy val tokenAccessor: CookieTokenAccessor = new CookieTokenAccessor(
    cookieSecureOption = play.api.Play.isProd(play.api.Play.current),
    cookieMaxAge       = Some(sessionTimeoutInSeconds)
  )

}