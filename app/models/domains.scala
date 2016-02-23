package models

import models.security.{SecurityPermission, SecurityRole}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import java.util.{UUID, Date}
import play.modules.reactivemongo.json.BSONFormats._


//case class User(_id: Option[BSONObjectID], userName: String) {
case class User(_id: Option[BSONObjectID], firstName: String, lastName: String, affiliation: String, email: String, password: String, roleNames: Seq[String], permissionNames: Seq[String]) {
  def userName = (firstName + " " + lastName)
  //def roles = roleNames.map(new SecurityRole(_))
  //def permissions = roleNames.map(new SecurityPermission(_))
}

object User {
  implicit val UserFormat = Json.format[User]

  implicit object UserIdentity extends BSONObjectIdentity[User] {
    def of(entity: User): Option[BSONObjectID] = entity._id
    protected def set(entity: User, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}

case class Message(_id: Option[BSONObjectID], content: String)

object Message {
  implicit val MessageFormat = Json.format[Message]

  implicit object MessageIdentity extends BSONObjectIdentity[Message] {
    def of(entity: Message): Option[BSONObjectID] = entity._id
    protected def set(entity: Message, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}

@Deprecated
case class MetaTypeStats(_id: Option[BSONObjectID], attributeName : String, intRatio: Double, longRatio: Double, floatRatio: Double, doubleRatio: Double, booleanRatio: Double, nullRatio: Double, valueRatioMap: Map[String, Double])

@Deprecated
object MetaTypeStats {
  implicit val MetaAndTypeStatsFormat = Json.format[MetaTypeStats]

  implicit object MetaAndTypeStatsIdentity extends BSONObjectIdentity[MetaTypeStats] {
    def of(entity: MetaTypeStats): Option[BSONObjectID] = entity._id
    protected def set(entity: MetaTypeStats, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}

case class Translation(_id : Option[BSONObjectID], original : String, translated : String) {
  override def toString = original + " -> " + translated
}

object Translation {
  implicit val TranslationFormat = Json.format[Translation]

  implicit object TranslationIdentity extends BSONObjectIdentity[Translation] {
    def of(entity: Translation): Option[BSONObjectID] = entity._id
    protected def set(entity: Translation, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}