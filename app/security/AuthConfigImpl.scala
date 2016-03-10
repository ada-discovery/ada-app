package security

import be.objectify.deadbolt.core.models.Role
import controllers.routes
import jp.t2v.lab.play2.auth.{AuthConfig, _}
import models.security.{CustomUser, UserManager}
import play.api.mvc.Results._
import play.api.mvc.{Request, RequestHeader, Result}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect._

trait AdaAuthConfig extends AuthConfig {

  def userManager: UserManager

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
  type User = CustomUser

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

  def currentUser(request: Request[_]): Future[Option[CustomUser]] = {
    // we can't call restoreUser, so we must retrieve the user manually
    val timeout = 120000 millis
    val currentToken: Option[AuthenticityToken] = tokenAccessor.extract(request)

    val userIdFuture = currentToken match{
      case Some(token) => idContainer.get(token)
      case None => Future(None)
    }

    val userId: Option[Id] = Await.result(userIdFuture, timeout)
    userId match{
      case Some(id) => resolveUser(id)
      case None => Future(None)
    }
  }

  /**
    * A function that returns a `User` object from an `Id`.
    * Retrieves user from Account class.
    *
    */
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] =
    userManager.findById(id)

  /**
    * Where to redirect the user after a successful login.
    * TODO: redirect to different page
    */
  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    //Future.successful(Redirect(routes.AppController.index()))
    Future.successful(Redirect(routes.UserProfileController.profile()))
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
    cookieSecureOption = false, // TODO: Introduce   play.api.Play.isProd(play.api.Play.current),
    cookieMaxAge       = Some(sessionTimeoutInSeconds)
  )
}