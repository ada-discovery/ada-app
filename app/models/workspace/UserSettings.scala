package models.workspace

import models.BSONObjectIdentity
import play.api.libs.json._
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

import play.modules.reactivemongo.json.BSONFormats._


case class Workspace(_id: Option[BSONObjectID], userId: String, filters: Seq[JsObject], treeProjections: Seq[JsObject])

object Workspace {
  implicit val WorkspaceFormat = Json.format[Workspace]

  implicit object WorkspaceIdentity extends BSONObjectIdentity[Workspace] {
    def of(entity: Workspace): Option[BSONObjectID] = entity._id
    protected def set(entity: Workspace, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}
