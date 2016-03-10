package security

import be.objectify.deadbolt.core.models.Subject
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

  def checkPermission[A](permissionValue: String, deadboltHandler: DeadboltHandler, request: Request[A]): Future[Boolean] = {
    val subjOpFuture: Future[Option[Subject]] = deadboltHandler.getSubject(request)
    subjOpFuture.map{subjOp =>
      subjOp match {
        case Some(subj) => subj.getPermissions.contains(permissionValue)
        case None => false
      }
    }
  }
}

object CustomDynamicResourceHandler {
  val handlers: Map[String, DynamicResourceHandler] =
    Map( )
}