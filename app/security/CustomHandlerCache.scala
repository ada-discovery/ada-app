package security

import javax.inject.Singleton

import javax.inject.{Inject, Named}
import be.objectify.deadbolt.scala.{HandlerKey, DeadboltHandler}
import be.objectify.deadbolt.scala.cache.HandlerCache
import controllers.AdaAuthConfig
import models.security.UserManager
import scala.concurrent.ExecutionContext.Implicits._

/**
  * Container and hook for deadbolt handlers
  */
@Singleton
class CustomHandlerCacheImpl @Inject() (myUserManager: UserManager) extends CustomHandlerCache with AdaAuthConfig {

  // a hook need by auth config
  override val userManager = myUserManager

  override protected def defaultHandler = new AdaDeadboltHandler(currentUser)
  override protected def userlessHandler = new AdaCustomUserlessDeadboltHandler(currentUser)
  override protected def alternativeDynamicResourceHandler = new AdaDeadboltHandler(currentUser, Some(CustomAlternativeDynamicResourceHandler))
}

abstract class CustomHandlerCache extends HandlerCache {

  protected def defaultHandler: DeadboltHandler
  protected def userlessHandler: DeadboltHandler
  protected def alternativeDynamicResourceHandler: DeadboltHandler

  val handlers: Map[Any, DeadboltHandler] = Map(HandlerKeys.defaultHandler -> defaultHandler,
                                                HandlerKeys.altHandler -> alternativeDynamicResourceHandler,
                                                HandlerKeys.userlessHandler -> userlessHandler)

  override def apply(): DeadboltHandler = defaultHandler

  override def apply(handlerKey: HandlerKey): DeadboltHandler = handlers(handlerKey)
}