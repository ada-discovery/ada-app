package security

import be.objectify.deadbolt.scala.{DeadboltHandler, DynamicResourceHandler}
import play.api.mvc.Request

import scala.collection.immutable.Map
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits._

/**
  * Created by jan.martens on 3/9/2016.
  */
class LdapDynamicResourceHandler extends DynamicResourceHandler
{
  def isAllowed[A](name: String, meta: String, handler: DeadboltHandler, request: Request[A]): Future[Boolean] = {
    CustomDynamicResourceHandler.handlers(name).isAllowed(name, meta, handler, request)
  }

  // todo implement this when demonstrating permissions
  def checkPermission[A](permissionValue: String, deadboltHandler: DeadboltHandler, request: Request[A]): Future[Boolean] = {
    //ldapusermanager.authorize(deadboltHandler.resolveUser().email, permissionValue)
    Future(false)
  }
}

object LdapDynamicResourceHandler {
  val handlers: Map[String, DynamicResourceHandler] =
    Map( )
}