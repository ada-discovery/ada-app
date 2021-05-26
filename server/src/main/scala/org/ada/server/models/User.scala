package org.ada.server.models

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.json.SerializableFormat
import play.api.libs.json.{Json, _}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

import java.util.UUID

/**
  *  User object - holds info about a user such as userId, email, roles, and  permissions.
  *
  * @param _id BSON ID of entry/ user
  * @param userId User's id.
  * @param oidcId An optional id obtained from an OIDC provider (if used), e.g. keycloak
  * @param name Full user's name
  * @param email Email of user (can be used to send notifications.
  * @param roles Roles for Deadbolt.
  * @param permissions Permissions for Deadbolt.
  */
case class User(
                 _id: Option[BSONObjectID] = None,
                 userId: String,
                 oidcId: Option[UUID] = None,
                 name: String,
                 email: String,
                 roles: Seq[String] = Nil,
                 permissions: Seq[String] = Nil,
                 locked: Boolean = false
)

object User {
  val userFormat: Format[User] = Json.format[User]
  implicit val serializableUserFormat: Format[User] = new SerializableFormat(userFormat.reads, userFormat.writes)

  implicit object UserIdentity extends BSONObjectIdentity[User] {
    def of(entity: User): Option[BSONObjectID] = entity._id
    protected def set(entity: User, id: Option[BSONObjectID]) = entity.copy(id)
  }
}
