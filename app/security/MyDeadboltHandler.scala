package security

import controllers.{AuthConfigImpl, routes}

import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

import be.objectify.deadbolt.scala.{DynamicResourceHandler, DeadboltHandler}
import be.objectify.deadbolt.core.models.Subject

import jp.t2v.lab.play2.auth
import jp.t2v.lab.play2.auth.{AuthenticityToken, LoginLogout, AuthElement}


import scala.concurrent.Await
import scala.concurrent.duration._


import models.security.Account

/**
  * Hooks for deadbolt
  *
  */
class MyDeadboltHandler(dynamicResourceHandler: Option[DynamicResourceHandler] = None) extends DeadboltHandler with AuthConfigImpl /*with AuthElement*/{

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
    * TODO: Cleanup
    *
    *
    * @param request
    * @tparam A
    * @return Current user, if logged in. None otherwise.
    */
  override def getSubject[A](request: Request[A]): Future[Option[Subject]] = {
    // we can't call restoreUser, so we must retrieve the user manually
    val timeout = 120000 millis
    val currentToken: Option[AuthenticityToken] = tokenAccessor.extract(request)

    val userIdFuture = currentToken match{
      case Some(token) => idContainer.get(token)
      case None => Future(None)
    }

    val userId: Option[Id] = Await.result(userIdFuture, timeout)
    val accountOpFuture: Future[Option[Account]] = userId match{
      case Some(id) => resolveUser(id)
      case None => Future(None)
    }

    accountOpFuture.map{
      case Some(acc) => Some(Account.toSubject(acc))
      case None => None
    }
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