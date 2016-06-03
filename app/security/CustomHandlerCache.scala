package security

import javax.inject.{Inject, Singleton}
import be.objectify.deadbolt.scala.{HandlerKey, DeadboltHandler}
import be.objectify.deadbolt.scala.cache.HandlerCache

import models.security.UserManager

@Singleton
class CustomHandlerCacheImpl @Inject() (val userManager: UserManager) extends HandlerCache with AdaAuthConfig {

  private val dynamicResourceHandler = new AdaDynamicResourceHandler

  private val handlers: Map[Any, DeadboltHandler] = Map(
    HandlerKeys.default -> new AdaOnFailureRedirectDeadboltHandler(currentUser, Some(dynamicResourceHandler)),
    HandlerKeys.unauthorizedStatus -> new AdaOnFailureUnauthorizedStatusDeadboltHandler(currentUser, Some(dynamicResourceHandler))
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