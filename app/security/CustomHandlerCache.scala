package security

import javax.inject.{Inject, Singleton}
import be.objectify.deadbolt.scala.{HandlerKey, DeadboltHandler}
import be.objectify.deadbolt.scala.cache.HandlerCache

import models.security.{UserManager, DeadboltUser}
import play.api.mvc.Request

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CustomHandlerCacheImpl @Inject() (val userManager: UserManager) extends HandlerCache with AdaAuthConfig {

  private val dynamicResourceHandler = new AdaDynamicResourceHandler

  private val currentDeadboltUser = {request: Request[_] => currentUser(request).map(_.map(DeadboltUser(_)))}

  // AdaDeadboltUser
  private val handlers: Map[Any, DeadboltHandler] = Map(
    HandlerKeys.default -> new AdaOnFailureRedirectDeadboltHandler(currentDeadboltUser, Some(dynamicResourceHandler)),
    HandlerKeys.unauthorizedStatus -> new AdaOnFailureUnauthorizedStatusDeadboltHandler(currentDeadboltUser, Some(dynamicResourceHandler))
  )

  override def apply = handlers(HandlerKeys.default)

  override def apply(handlerKey: HandlerKey) = handlers(handlerKey)
}

/**
 *  Deadbolt handler key defintions
 */
object HandlerKeys {

  val default = Key("defaultHandler")              // key for default handler; handler retrieves user, authority and permission informaation from userRepo
  val unauthorizedStatus = Key("unauthorizedStatus")

  case class Key(name: String) extends HandlerKey
}