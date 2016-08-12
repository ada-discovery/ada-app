package dataaccess

import dataaccess.mongo.{MongoAsyncCrudRepo, MongoAsyncCrudExtraRepo}
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

object RepoTypes {
  type JsonCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]

  type DictionaryRootRepo = MongoAsyncCrudExtraRepo[Dictionary, BSONObjectID]
  type DictionaryFieldRepo = AsyncCrudRepo[Field, String]
  type DictionaryCategoryRepo = AsyncCrudRepo[Category, BSONObjectID]

  type DataSetSettingRepo = AsyncCrudRepo[DataSetSetting, BSONObjectID]
}