package dataaccess

import dataaccess.mongo.{MongoAsyncCrudRepo, MongoAsyncCrudExtraRepo}
import models._
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

object RepoTypes {
  type JsonCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]

  type DictionaryRootRepo = MongoAsyncCrudExtraRepo[Dictionary, BSONObjectID]

  type FieldRepo = AsyncCrudRepo[Field, String]
  type CategoryRepo = AsyncCrudRepo[Category, BSONObjectID]
  type FilterRepo = AsyncCrudRepo[Filter, BSONObjectID]
  type DataViewRepo = AsyncCrudRepo[DataView, BSONObjectID]

  type DataSetMetaInfoRepo = AsyncCrudRepo[DataSetMetaInfo, BSONObjectID]
  type DataSpaceMetaInfoRepo = AsyncCrudRepo[DataSpaceMetaInfo, BSONObjectID]

  type DataSetSettingRepo = AsyncCrudRepo[DataSetSetting, BSONObjectID]

  type UserRepo = AsyncCrudRepo[User, BSONObjectID]
}