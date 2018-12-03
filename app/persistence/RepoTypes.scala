package persistence

import models._
import models.workspace.Workspace
import reactivemongo.bson.BSONObjectID
import models.ml.classification.Classification
import models.HtmlSnippet
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning
import org.incal.core.dataaccess.{AsyncCrudRepo, AsyncStreamRepo}

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

  type HtmlSnippetRepo = AsyncCrudRepo[HtmlSnippet, BSONObjectID]

  // experimental
//  type StudentDistRepo = DistributedRepo[Student, BSONObjectID]
//  type JsObjectDistRepo = DistributedRepo[JsObject, BSONObjectID]
}