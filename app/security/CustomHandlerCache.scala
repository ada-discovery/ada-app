package security

import javax.inject.Singleton

import javax.inject.{Inject, Named}
import be.objectify.deadbolt.scala.{HandlerKey, DeadboltHandler}
import be.objectify.deadbolt.scala.cache.HandlerCache
import com.google.inject.ImplementedBy
import models.security.UserManager
import scala.concurrent.ExecutionContext.Implicits._

/**
  * Container and hook for deadbolt handlers
  */
@Singleton
class CustomHandlerCacheImpl @Inject() (myUserManager: UserManager) extends CustomHandlerCache with AdaAuthConfig {

  // a hook need by auth config
  override val userManager = myUserManager

  override def defaultHandler = new AdaDeadboltHandler(currentUser)
  override def userlessHandler = new AdaUserlessDeadboltHandler(currentUser)
  override def alternativeDynamicResourceHandler = new AdaDeadboltHandler(currentUser, Some(CustomAlternativeDynamicResourceHandler))
}

@ImplementedBy(classOf[CustomHandlerCacheImpl])
trait CustomHandlerCache extends HandlerCache {

  def defaultHandler: DeadboltHandler
  def userlessHandler: DeadboltHandler
  def alternativeDynamicResourceHandler: DeadboltHandler

  val handlers: Map[Any, DeadboltHandler] = Map(HandlerKeys.defaultHandler -> defaultHandler,
                                                HandlerKeys.altHandler -> alternativeDynamicResourceHandler,
                                                HandlerKeys.userlessHandler -> userlessHandler)

  override def apply(): DeadboltHandler = defaultHandler

  override def apply(handlerKey: HandlerKey): DeadboltHandler = handlers(handlerKey)
}


/**
 *  Deadbolt handler key defintions
 */
object HandlerKeys {

  val defaultHandler = Key("defaultHandler")              // key for default user
  val altHandler = Key("altHandler")                      // alternative handler; to be changed
  val userlessHandler = Key("userlessHandler")            // if no user logged in

  case class Key(name: String) extends HandlerKey
}