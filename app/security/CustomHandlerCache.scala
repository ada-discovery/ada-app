package security

import javax.inject.Singleton

import javax.inject.{Inject, Named}
import be.objectify.deadbolt.scala.{HandlerKey, DeadboltHandler}
import be.objectify.deadbolt.scala.cache.HandlerCache
import com.google.inject.ImplementedBy
import ldap.LdapUserManager
import models.security.UserManager
import scala.concurrent.ExecutionContext.Implicits._

/**
  * Container and hook for deadbolt handlers
  */
@Singleton
class CustomHandlerCacheImpl @Inject() (myUserManager: UserManager, myLdapUserManager: LdapUserManager) extends CustomHandlerCache with AdaAuthConfig {

  // a hook need by auth config
  override val userManager = myUserManager
  //override val LdapUserManager = myLdapUserManager

  override def defaultHandler = new AdaDeadboltHandler(currentUser)
  override def ldapHandler = new LdapDeadboltHandler(currentUser)
}

@ImplementedBy(classOf[CustomHandlerCacheImpl])
trait CustomHandlerCache extends HandlerCache {

  def defaultHandler: DeadboltHandler
  def ldapHandler: DeadboltHandler

  val handlers: Map[Any, DeadboltHandler] = Map(HandlerKeys.defaultHandler -> defaultHandler,
                                                HandlerKeys.ldapHandler -> ldapHandler)

  override def apply(): DeadboltHandler = defaultHandler

  override def apply(handlerKey: HandlerKey): DeadboltHandler = handlers(handlerKey)
}


/**
 *  Deadbolt handler key defintions
 */
object HandlerKeys {

  val defaultHandler = Key("defaultHandler")              // key for default handler; handler retrieves user, authority and permission informaation from userRepo
  val ldapHandler = Key("ldapHandler")                    // key for ldap handler; handler retrieves user, authority and permission information from ldap server

  case class Key(name: String) extends HandlerKey
}