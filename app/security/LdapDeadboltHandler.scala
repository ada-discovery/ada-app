package security

import be.objectify.deadbolt.core.models.Subject
import be.objectify.deadbolt.scala.{DeadboltHandler, DynamicResourceHandler}
import controllers.routes
import play.api.mvc.{Results, Result, Request}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._


/**
  * Created by jan.martens on 3/9/2016.
  */
class LdapDeadboltHandler (
    getCurrentUser: Request[_] => Future[Option[Subject]],
    dynamicResourceHandler: Option[DynamicResourceHandler] = None
  ) extends DeadboltHandler {

  /**
    * Pre-authorization task. May block further execution.
    * Empty right now.
    *
    * @param request
    * @return
    */
  override def beforeAuthCheck[A](request: Request[A]): Future[Option[Result]] = {
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
    getCurrentUser(request)
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