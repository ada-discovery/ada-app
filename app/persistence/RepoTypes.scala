package persistence

import models._
import models.security.CustomUser
import models.workspace.Workspace
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
  type DictionaryFieldRepo = SubordinateObjectRepo[Field, String]
  type DictionaryCategoryRepo = SubordinateObjectRepo[Category, BSONObjectID]

  type DataSpaceMetaInfoRepo = MongoAsyncCrudExtraRepo[DataSpaceMetaInfo, BSONObjectID]
  type DataSetMetaInfoRepo = SubordinateObjectRepo[DataSetMetaInfo, BSONObjectID]
  type DataSetSettingRepo = AsyncCrudRepo[DataSetSetting, BSONObjectID]

  // experimental
  type StudentDistRepo = DistributedRepo[Student, BSONObjectID]
  type JsObjectDistRepo = DistributedRepo[JsObject, BSONObjectID]
}
