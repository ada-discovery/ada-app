package security

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import be.objectify.deadbolt.scala.{HandlerKey, DeadboltHandler}
import be.objectify.deadbolt.scala.cache.HandlerCache

import models.security.UserManager


/**
  * Container and hook for deadbolt handlers
  */
@Singleton
class CustomHandlerCacheImpl @Inject() (usrmmanager: UserManager) extends CustomHandlerCache with AdaAuthConfig {
  // a hook need by auth config
  override val userManager = usrmmanager
  override def defaultHandler = new AdaDeadboltHandler(currentUser, Some(new AdaDynamicResourceHandler))
}

@ImplementedBy(classOf[CustomHandlerCacheImpl])
trait CustomHandlerCache extends HandlerCache {

  def defaultHandler: DeadboltHandler

  val handlers: Map[Any, DeadboltHandler] = Map(HandlerKeys.defaultHandler -> defaultHandler)

  override def apply(): DeadboltHandler = defaultHandler

  override def apply(handlerKey: HandlerKey): DeadboltHandler = handlers(handlerKey)
}


/**
 *  Deadbolt handler key defintions
 */
object HandlerKeys {

  val defaultHandler = Key("defaultHandler")              // key for default handler; handler retrieves user, authority and permission informaation from userRepo

  case class Key(name: String) extends HandlerKey
}