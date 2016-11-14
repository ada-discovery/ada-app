package persistence

import dataaccess._
import models._
import dataaccess.User
import models.workspace.Workspace
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import dataaccess.mongo.MongoAsyncCrudExtraRepo

/**
 * Common repo type shortcuts
 */
object RepoTypes {
//  type JsObjectCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]
  type TranslationRepo = AsyncCrudRepo[Translation, BSONObjectID]

  type MessageRepo = AsyncStreamRepo[Message, BSONObjectID]
  type WorkspaceRepo = AsyncCrudRepo[Workspace, BSONObjectID]

  type DataSpaceMetaInfoRepo = MongoAsyncCrudExtraRepo[DataSpaceMetaInfo, BSONObjectID]
  type DataSetMetaInfoRepo = AsyncCrudRepo[DataSetMetaInfo, BSONObjectID]
  type DataSetImportRepo = AsyncCrudRepo[DataSetImport, BSONObjectID]

  // experimental
  type StudentDistRepo = DistributedRepo[Student, BSONObjectID]
  type JsObjectDistRepo = DistributedRepo[JsObject, BSONObjectID]
}
