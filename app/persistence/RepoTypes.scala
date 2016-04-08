package persistence

import models._
import models.security.CustomUser
import models.workspace.Workspace
import persistence.dataset.DictionarySubordinateRepo
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

/**
 * Common repo type shortcuts
 */
object RepoTypes {
  type JsObjectCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]
  type TranslationRepo = AsyncCrudRepo[Translation, BSONObjectID]
  type UserRepo = AsyncCrudRepo[CustomUser, BSONObjectID]
  type MessageRepo = AsyncStreamRepo[Message, BSONObjectID]
  type WorkspaceRepo = AsyncCrudRepo[Workspace, BSONObjectID]
  type DictionaryRootRepo = MongoAsyncCrudExtraRepo[Dictionary, BSONObjectID]
  type DictionaryFieldRepo = DictionarySubordinateRepo[Field, String]
  type DictionaryCategoryRepo = DictionarySubordinateRepo[Category, BSONObjectID]
  type DataSetMetaInfoRepo = AsyncCrudRepo[DataSetMetaInfo, BSONObjectID]

  // experimental
  type StudentDistRepo = DistributedRepo[Student, BSONObjectID]
  type JsObjectDistRepo = DistributedRepo[JsObject, BSONObjectID]
}
