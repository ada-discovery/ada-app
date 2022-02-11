package org.ada.server.models

import org.ada.server.dataaccess.BSONObjectIdentity
import play.api.libs.json.{Json, OFormat}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

case class UserSettings(
  _id: Option[BSONObjectID] = None,
  defaultLoginRights: LoginRights.Value
  )

object UserSettings {
  implicit val userSettingsFormat: OFormat[UserSettings] = Json.format[UserSettings]

  implicit object UserSettingsIdentity extends BSONObjectIdentity[UserSettings] {
    override def of(entity: UserSettings): Option[BSONObjectID] = entity._id
    override protected def set(entity: UserSettings, id: Option[BSONObjectID]): UserSettings = entity.copy(_id = id)
  }
}
