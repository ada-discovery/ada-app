package security

import play.api.mvc.{Request, Result, Results}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

import be.objectify.deadbolt.scala.{DynamicResourceHandler, DeadboltHandler}
import be.objectify.deadbolt.core.models.Subject


//import


import models.security.AppUser
import models.security.SecurityRole

/**
  * Hooks for deadbolt
  *
  *
  */
class MyDeadboltHandler(dynamicResourceHandler: Option[DynamicResourceHandler] = None) extends DeadboltHandler {

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
    *
    * @param request
    * @tparam A
    * @return Current user, if logged in. None otherwise.
    */
  override def getSubject[A](request: Request[A]): Future[Option[Subject]] = {
    // TODO: change to actual use
    // right now, there *never* is a valid subject
    //request.session.get("user")
    //PlayAuthenticate.getUser(request)


    Future(None)
  }

  /**
    * Action if user is not authorized.
    *
    * @param request
    * @tparam A
    * @return
    */
  // TODO: if valid user is in cookie, log him/her out instead and redirect to login page.
  def onAuthFailure[A](request: Request[A]): Future[Result] = {
    Future {Results.Forbidden("Access denied")}
  }



}