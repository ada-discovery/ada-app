package security

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import be.objectify.deadbolt.scala.{HandlerKey, DeadboltHandler}
import be.objectify.deadbolt.scala.cache.HandlerCache

import models.security.{CustomUser, UserManager}
import ldap.AdaLdapUserServer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import play.api.mvc.Request
import scala.concurrent.duration._



/**
  * Container and hook for deadbolt handlers
  */
@Singleton
class CustomHandlerCacheImpl @Inject() (myUserManager: UserManager, myAdaLdapUserServer: AdaLdapUserServer) extends CustomHandlerCache with AdaAuthConfig {

  // a hook need by auth config
  override val userManager = myUserManager

  //override def defaultHandler = new AdaDeadboltHandler(currentUser, Some(new AdaDynamicResourceHandler))
  override def ldapHandler = new AdaDeadboltHandler(currentUserLdap, Some(new AdaDynamicResourceHandler))
  override def defaultHandler = ldapHandler


  /**
    * Resolve user from currently held token and access ldap for authorization.
    * @param request Request header with cookie.
    * @return User identified by held token, if found in ldap server.
    */
  def currentUserLdap(request: Request[_]): Future[Option[CustomUser]] ={
    val timeout = 120000 millis
    val idOpFuture: Future[Option[Id]] = getUserFromToken(request)
    val idOp: Option[String] = Await.result(idOpFuture, timeout)

    idOp match{
      case Some(id) => resolveUserLdap(id)
      case None => Future(None)
    }
  }

  /**
    * Access ldap user server to check authority.
    * @param id User identifier (user name string).
    * @return User, if found in ldap server.
    */
  def resolveUserLdap(id: Id): Future[Option[User]] = {
    myAdaLdapUserServer.findById(id)
  }
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