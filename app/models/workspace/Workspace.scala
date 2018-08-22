package models.workspace

import org.incal.core.FilterCondition
import models.Filter.filterConditionFormat
import dataaccess.BSONObjectIdentity
import org.incal.core.FilterCondition
import play.api.libs.json._
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class Workspace(
  _id: Option[BSONObjectID],
  userId: String,
  collaborators: UserGroup,
  filters: Seq[FilterCondition],
  treeProjections: Seq[JsObject]
)

object Workspace {
  val emptyUserGroup = UserGroup(None, "empty", None, Seq())

  implicit val WorkspaceFormat = Json.format[Workspace]

  implicit object WorkspaceIdentity extends BSONObjectIdentity[Workspace] {
    def of(entity: Workspace): Option[BSONObjectID] = entity._id
    protected def set(entity: Workspace, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}
