package security

import be.objectify.deadbolt.scala.{DynamicResourceHandler, DeadboltHandler}
import models.security.SecurityPermission
import collection.immutable.Map
import play.api.mvc.Request

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 *
 * @author Steve Chaloner (steve@objectify.be)
 */
class CustomDynamicResourceHandler extends DynamicResourceHandler
{
  def isAllowed[A](name: String, meta: String, handler: DeadboltHandler, request: Request[A]): Future[Boolean] = {
    CustomDynamicResourceHandler.handlers(name).isAllowed(name, meta, handler, request)
  }

  // todo implement this when demonstrating permissions
  def checkPermission[A](permissionValue: String, deadboltHandler: DeadboltHandler, request: Request[A]): Future[Boolean] = {
    //deadboltHandler.getSubject(request).getPermissions().contains(SecurityPermission(permissionValue))
    Future(false)
  }
}

object CustomDynamicResourceHandler {
  val handlers: Map[String, DynamicResourceHandler] =
    Map( )
}