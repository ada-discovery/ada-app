package modules

import com.unboundid.ldap.sdk._
import ldap.LdapActions
import play.api._

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

import play.api.{Configuration, Environment};
import play.api.inject.{Binding, Module};

import scala.collection.Seq;

/**
  * TODO: reimplement as play.api.inject.Module (play.api.Plugin is deprecated)
  */
class LdapModule(app: Application) extends Module {

  // overwrite these values
  val defaultPort: Int = 389

  lazy val host: Option[String] = app.configuration.getString("ldap.host")                // url of ldap directory
  lazy val port: Option[Int] = app.configuration.getInt("ldap.port")                      // port of ldap directory
  lazy val timeout: Option[Int] = app.configuration.getInt("ldap.timeout")                // request timeout
  lazy val bindDn: Option[String] = Some("admin@mail")                                    // overwrite with value from user repo
  lazy val password: Option[String] = Some("123456")                                      // overwrite with value from user repo

  lazy val connection: LDAPConnection = {
    if (host.isDefined && bindDn.isDefined && password.isDefined)
      new LDAPConnection(host.get, port.getOrElse(defaultPort), bindDn.get, password.get)
    else
      throw new PlayException("LdapPlugin Initialization Error", s"ldap.host = ${host}, ldap.bindDn = ${bindDn} and ldap.password = ${password} are required configs")
  }

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[LdapActions].toSelf
  )
}

object LdapModule extends LdapOperations

trait LdapOperations {

  def filter(attributeName: String, attributeValue: String, baseDn: String, resultAttributes: String*)(implicit  app: Application, ec: ExecutionContext) = {
    val filter: Filter = Filter.createEqualityFilter(attributeName, attributeValue)
    val searchRequest: SearchRequest = new SearchRequest(baseDn, SearchScope.SUB, filter, resultAttributes: _*)

    for {
      entry <- current.connection.search(searchRequest).getSearchEntries
    } yield (entry)
  }

  def current(implicit app: Application): LdapModule = app.plugin[LdapModule] match {
    case Some(plugin) => plugin
    case _ => throw new PlayException("LdapPlugin Error", "The LdapPlugin has not been initialized! Please edit your conf/play.plugins file and add the following line: '1000:play.modules.ldap.LdapPlugin' (1000 is an arbitrary priority and may be changed to match your needs).")
  }

}