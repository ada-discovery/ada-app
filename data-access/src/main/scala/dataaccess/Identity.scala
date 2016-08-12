package dataaccess

import java.util.UUID

import play.api.libs.json.{Json, JsObject}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

/**
 * Type class providing identity manipulation methods
 */
trait Identity[E, ID] extends Serializable {
  def name: String
  def of(entity: E): Option[ID]
  def set(entity: E, id: ID): E = set(entity, Some(id))
  def clear(entity: E) = set(entity, None)
  def next: ID

  protected def set(entity: E, id: Option[ID]): E
}

trait UUIDIdentity[E] extends Identity[E, UUID] {
  val name = "uuid" // default value
  def next = UUID.randomUUID()
}

trait BSONObjectIdentity[E] extends Identity[E, BSONObjectID] {
  val name = "_id" // must be like that!
  def next = BSONObjectID.generate
}