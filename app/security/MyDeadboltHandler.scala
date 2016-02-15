package security

import controllers.{AuthConfigImpl, routes}
import jp.t2v.lab.play2.auth.AuthElement

import play.api.mvc.{Request, Result, Results}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

import be.objectify.deadbolt.scala.{DynamicResourceHandler, DeadboltHandler}
import be.objectify.deadbolt.core.models.Subject

import jp.t2v.lab.play2.auth


/**
  * Hooks for deadbolt
  *
  */
class MyDeadboltHandler(dynamicResourceHandler: Option[DynamicResourceHandler] = None) extends DeadboltHandler with AuthConfigImpl {

  /**
    * Pre-authorization task. May block further execution.
    * Empty right now.
    *
    * @param request
    * @tparam A
    * @return
    */
  override def beforeAuthCheck[A](request: Request[A]) = {
    Future(None)
  }

  /**
    * Hook for dynamic constraint types.
    *
    * @param request
    * @tparam A
    * @return
    */
  override def getDynamicResourceHandler[A](request: Request[A]): Future[Option[DynamicResourceHandler]] = {
    Future(dynamicResourceHandler.orElse(Some(new MyDynamicResourceHandler())))
  }

  /**
    * Retrieves the current user, wrapped into an Option.
    * TODO: Not implemented yet, will always return None
    *
    *
    * @param request
    * @tparam A
    * @return Current user, if logged in. None otherwise.
    */
  override def getSubject[A](request: Request[A]): Future[Option[Subject]] = {
    // TODO: change for actual use
    // right now, there never is a valid subject



    Future(None)
  }

  /**
    * Action if user is not authorized.
    * Redirects user to login form if not authorized.
    *
    * @param request request leading to failure.
    * @tparam A
    * @return Redirect to login form.
    */
  def onAuthFailure[A](request: Request[A]): Future[Result] = {
    Future(Results.Redirect(routes.AuthController.login))
  }

}