package security

import be.objectify.deadbolt.core.models.Subject
import be.objectify.deadbolt.scala.{DynamicResourceHandler, DeadboltHandler}

import collection.immutable.Map
import play.api.mvc.Request
import play.api.Logger


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 *
 * @author Steve Chaloner (steve@objectify.be)
 */
class AdaDynamicResourceHandler extends DynamicResourceHandler
{
  def isAllowed[A](name: String, meta: String, handler: DeadboltHandler, request: Request[A]): Future[Boolean] = {
    val successFuture : Future[Boolean] = AdaDynamicResourceHandler.handlers(name).isAllowed(name, meta, handler, request)
    successFuture.map((success: Boolean) =>
      if(!success){
        Logger.error(s"Unallowed access by [$name].")
      }
    )
    successFuture
  }

  def checkPermission[A](permissionValue: String, deadboltHandler: DeadboltHandler, request: Request[A]): Future[Boolean] = {
    val subjOpFuture: Future[Option[Subject]] = deadboltHandler.getSubject(request)
    subjOpFuture.map{subjOp =>
      if(subjOp.isDefined){
        val success: Boolean = (subjOp.get.getPermissions.contains(permissionValue))
        if(!success){
          val username = subjOp.get.getIdentifier
          Logger.error(s"Unauthorized access; user [$username] is missing permission [$permissionValue].")
        }
        success
      }else{
        Logger.error("Unauthorized access by unregistered user.")
        false
      }
    }
  }
}

object AdaDynamicResourceHandler {
  val handlers: Map[String, DynamicResourceHandler] =
    Map( )
}