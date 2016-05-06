package ldap


import javax.inject.Inject

import com.unboundid.ldap.sdk._
import ldap.LdapUtil.LdapConverter

import persistence.{SyncReadonlyRepo, Sort}
import play.api.libs.json.JsObject

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  *
  */
trait LdapRepo[T <: LdapDN] extends SyncReadonlyRepo[T, String] with ObjectCache[T]{

  def converter: LdapConverter[T] = ???
  def connector: LdapConnector = ???
  def settings: LdapSettings = ???

  val dit: String = settings.dit
  override val updateInterval: Int = settings.updateInterval
  override val updateCall: (() => Traversable[T]) = requestList

  def get(id: String): Option[T] = {
    getCache(false).find{entry => (entry.getDN == id)}
  }

  // TODO argument and criteria conversion
  // TODO add filtering
  def find(
    criteria: Option[JsObject] = None,
    orderBy: Option[Seq[Sort]] = None,
    projection : Option[JsObject] = None,
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Traversable[T] = {
    getCache(false)
  }

  def count(criteria: Option[JsObject] = None): Int = {
    find(criteria).size
  }

  // override for more specific behaviour
  def requestList(): Traversable[T] = {
    val baseDN = dit
    val scope = SearchScope.SUB
    val filter = Filter.create("(objectClass=*)")
    val request: SearchRequest = new SearchRequest(baseDN, scope, filter)

    val entries: Traversable[Entry] = connector.dispatchRequest(request)
    LdapUtil.convertAndFilter(entries, converter)
  }
}



