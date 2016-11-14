package dataaccess

import dataaccess.mongo.{MongoAsyncCrudRepo, MongoAsyncCrudExtraRepo}
import models.{Category, Field, Dictionary, DataSetSetting}
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import models.{Filter, DataView}

object RepoTypes {
  type JsonCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]

  type DictionaryRootRepo = MongoAsyncCrudExtraRepo[Dictionary, BSONObjectID]

  type FieldRepo = AsyncCrudRepo[Field, String]
  type CategoryRepo = AsyncCrudRepo[Category, BSONObjectID]
  type FilterRepo = AsyncCrudRepo[Filter, BSONObjectID]
  type DataViewRepo = AsyncCrudRepo[DataView, BSONObjectID]

  type DataSetSettingRepo = AsyncCrudRepo[DataSetSetting, BSONObjectID]

  type UserRepo = AsyncCrudRepo[User, BSONObjectID]
}