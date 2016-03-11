package security

import controllers.routes

import play.api.mvc._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import be.objectify.deadbolt.scala.{DynamicResourceHandler, DeadboltHandler}
import be.objectify.deadbolt.core.models.Subject

/**
  * Hooks for deadbolt
  */
class AdaDeadboltHandler(
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
    val subjOpFuture: Future[Option[Subject]] = getSubject(request)

    subjOpFuture.map { subjOp: Option[Subject] =>
      if(subjOp.isDefined){
        val username = subjOp.get.getIdentifier
        Logger.error(s"Unauthorized access by [$username].")
      }else{
        Logger.error("Unauthorized access by unregistered user.")
      }
    }

    Future(Results.Redirect(routes.AuthController.unauthorized))
  }
}