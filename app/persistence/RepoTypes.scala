package persistence

import dataaccess._
import models._
import dataaccess.User
import models.workspace.Workspace
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import dataaccess.mongo.MongoAsyncCrudExtraRepo
import models.ml.classification.Classification
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning

/**
 * Common repo type shortcuts
 */
object RepoTypes {
//  type JsObjectCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]
  type TranslationRepo = AsyncCrudRepo[Translation, BSONObjectID]

  type MessageRepo = AsyncStreamRepo[Message, BSONObjectID]
  type UserSettingsRepo = AsyncCrudRepo[Workspace, BSONObjectID]

  type DataSetImportRepo = AsyncCrudRepo[DataSetImport, BSONObjectID]

  type ClassificationRepo = AsyncCrudRepo[Classification, BSONObjectID]
  type RegressionRepo = AsyncCrudRepo[Regression, BSONObjectID]
  type UnsupervisedLearningRepo = AsyncCrudRepo[UnsupervisedLearning, BSONObjectID]

  // experimental
//  type StudentDistRepo = DistributedRepo[Student, BSONObjectID]
//  type JsObjectDistRepo = DistributedRepo[JsObject, BSONObjectID]
}