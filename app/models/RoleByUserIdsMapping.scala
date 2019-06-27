package models

import reactivemongo.bson.BSONObjectID

case class RoleByUserIdsMapping(mapping: Map[Role.Value, Traversable[BSONObjectID]])
