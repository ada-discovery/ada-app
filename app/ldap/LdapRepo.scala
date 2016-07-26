package ldap


import com.unboundid.ldap.sdk._
import ldap.LdapUtil.LdapConverter
import models.Criterion

import persistence._
import play.api.libs.json._
import _root_.util.ObjectCache


import scala.collection.Set
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Trait for creating Ldap repos.
  * Ldap repos implement ObjectCache to limit the number of actual Ldap operations.
  * The template class must override LdapDN to provide a DN and json formatter.
  * Make sure to provide/ inject a LdapConnector and a conversion method for LdapEntries.
  * Extends AsyncReadOnnlyRepo and can thus be used in ReadOnlyControllers.
  */
trait LdapRepo[T <: LdapDN] extends AsyncReadonlyRepo[T, String] with ObjectCache[T]{

  def converter: LdapConverter[T] = ???
  def connector: LdapConnector = ???
  def settings: LdapSettings = connector.ldapsettings

  val dit: String = settings.dit
  override val updateInterval: Int = settings.updateInterval
  override val updateCall: (() => Traversable[T]) = requestList

  def get(id: String): Future[Option[T]] = {
    Future(getCache(false).find{entry => (entry.getDN == id)})
  }

  // TODO projection
  // TODO fix filtering
  override def find(
    criteria: Seq[Criterion[Any]],
    orderBy: Seq[Sort],
    projection : Traversable[String],
    limit: Option[Int] = None,
    page: Option[Int] = None
  ): Future[Traversable[T]] = {

    val entries = getCache(false)
    val entriesFiltered =
      criteria match {
        case Nil => entries
        case _ => {
          val jsonFilterKeys = criteria.map(_.fieldName)
          val jsonFilter = { (entry: T) =>
            val entryJson: JsValue = entry.toJson
            jsonFilterKeys.exists { key: String =>
              (entryJson \ key).toOption.map(value =>
                criteria.exists(c => c.fieldName.equals(key) && c.value.equals(value))
              ).getOrElse(false)
            }
          }
          entries.filter(jsonFilter)
        }
      }

    val entriesOrdered: Seq[T] =
      orderBy.headOption match {
        case Some(sort) => {
            sort match{
              case AscSort(_) => entriesFiltered.toSeq.sortWith{(a: T, b: T) => a.getDN < b.getDN}
              case DescSort(_) => entriesFiltered.toSeq.sortWith{(a: T, b: T) => a.getDN > b.getDN}
            }
          }
        case None => entriesFiltered.toSeq
      }

    val entryLimit: Int = limit match {
      case Some(limit) => limit
      case None => entriesOrdered.size
    }

    val entryPage: Traversable[T] = page match {
      case Some(page) => entriesOrdered.slice(page*entryLimit , (page+1)*entryLimit)
      case None => entries
    }
    Future(entryPage)
  }

  /**
    * Count all Objects mathcing the given criteria.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @return Number of matching elements.
    */
  def count(criteria: Seq[Criterion[Any]]): Future[Int] = {
    val res = find(criteria)
    res.map(t => t.size)
  }

  /**
    * Request a new Traversable of objects.
    * Override this to be specific about the LdapEntries you want to retrieve.
    *
    * @return Converted LdapEntries.
    */
  def requestList(): Traversable[T] = {
    val scope = SearchScope.SUB
    val filter = Filter.create("(objectClass=*)")
    val request: SearchRequest = new SearchRequest(dit, scope, filter)

    val entries: Traversable[Entry] = connector.dispatchRequest(request)
    LdapUtil.convertAndFilter(entries, converter)
  }
}