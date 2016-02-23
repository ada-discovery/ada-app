package security

import controllers.{AuthConfigImpl, routes}

import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import be.objectify.deadbolt.scala.{DynamicResourceHandler, DeadboltHandler}
import be.objectify.deadbolt.core.models.Subject

import jp.t2v.lab.play2.auth.AuthenticityToken


/**
  * Hooks for deadbolt
  *
  */
class CustomDeadboltHandler(dynamicResourceHandler: Option[DynamicResourceHandler] = None) extends DeadboltHandler with AuthConfigImpl {

  /**
    * Pre-authorization task. May block further execution.
    * Empty right now.
    *
    * @param request
    * @return
    */
  override def beforeAuthCheck[A](request: Request[A]) = {
    Future(None)
  }

  /**
    * Hook for dynamic constraint types.
    *
    * @param request
    * @return
    */
  override def getDynamicResourceHandler[A](request: Request[A]): Future[Option[DynamicResourceHandler]] = {
    Future(dynamicResourceHandler.orElse(Some(new CustomDynamicResourceHandler())))
  }

  /**
    * Retrieves the current user, wrapped in an Option.
    *
    * @param request Current request.
    * @return Current user, if logged in. None otherwise.
    */
  override def getSubject[A](request: Request[A]): Future[Option[Subject]] = {
    currentUser(request)
  }

  /**
    * TODO: execute more meaningful action.
    * Action if user is not authorized.
    * Redirects user to login form if not authorized.
    *
    * @param request request leading to failure.
    * @return Redirect to login form.
    */
  def onAuthFailure[A](request: Request[A]): Future[Result] = {
    Future(Results.Redirect(routes.AuthController.unauthorized))
  }

}