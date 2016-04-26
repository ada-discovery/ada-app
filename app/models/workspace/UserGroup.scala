package models.workspace

import _root_.util.FilterSpec
import models.BSONObjectIdentity
import play.api.libs.json._
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

import play.modules.reactivemongo.json.BSONFormats._

/**
  * Container for group information.
  * @param _id BSON id, if group loaded from database.
  * @param groupName Short name of group.
  * @param description More detailed description of the group.
  * @param members List of group members
  */
case class UserGroup (_id: Option[BSONObjectID], groupName: String, description: Option[String], members: Seq[String], nested: Seq[String]=Seq[String]())

object UserGroup {
  implicit val UserGroupFormat = Json.format[UserGroup]

  implicit object UserGroupIdentity extends BSONObjectIdentity[UserGroup] {
    def of(entity: UserGroup): Option[BSONObjectID] = entity._id
    protected def set(entity: UserGroup, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}