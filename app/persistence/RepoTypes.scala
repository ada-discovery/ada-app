package persistence

import models._
import models.workspace.Workspace
import reactivemongo.bson.BSONObjectID
import models.HtmlSnippet
import models.ml.regression.RegressionResult
import models.ml.unsupervised.UnsupervisedLearning
import org.incal.core.dataaccess.{AsyncCrudRepo, AsyncStreamRepo}
import org.incal.spark_ml.models.classification.ClassificationModel
import org.incal.spark_ml.models.regression.RegressionModel
import org.incal.spark_ml.models.result.{ClassificationResult, RegressionResult}

/**
 * Common repo type shortcuts
 */
object RepoTypes {
//  type JsObjectCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]
  type TranslationRepo = AsyncCrudRepo[Translation, BSONObjectID]

  type MessageRepo = AsyncStreamRepo[Message, BSONObjectID]
  type UserSettingsRepo = AsyncCrudRepo[Workspace, BSONObjectID]

  type DataSetImportRepo = AsyncCrudRepo[DataSetImport, BSONObjectID]

  type ClassificationRepo = AsyncCrudRepo[ClassificationModel, BSONObjectID]
  type RegressionRepo = AsyncCrudRepo[RegressionModel, BSONObjectID]
  type UnsupervisedLearningRepo = AsyncCrudRepo[UnsupervisedLearning, BSONObjectID]

  type ClassificationResultRepo = AsyncCrudRepo[ClassificationResult, BSONObjectID]
  type RegressionResultRepo = AsyncCrudRepo[RegressionResult, BSONObjectID]

  type HtmlSnippetRepo = AsyncCrudRepo[HtmlSnippet, BSONObjectID]

  // experimental
//  type StudentDistRepo = DistributedRepo[Student, BSONObjectID]
//  type JsObjectDistRepo = DistributedRepo[JsObject, BSONObjectID]
}