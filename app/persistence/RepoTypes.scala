package persistence

import models._
import models.workspace.Workspace
import reactivemongo.bson.BSONObjectID
import models.HtmlSnippet
import models.ml.regression.RegressionResult
import models.ml.unsupervised.UnsupervisedLearning
import org.incal.core.dataaccess.{AsyncCrudRepo, AsyncStreamRepo}
import org.incal.spark_ml.models.classification.Classification
import org.incal.spark_ml.models.regression.Regression
import org.incal.spark_ml.models.results.{ClassificationResult, RegressionResult}

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

  type ClassificationResultRepo = AsyncCrudRepo[ClassificationResult, BSONObjectID]
  type RegressionResultRepo = AsyncCrudRepo[RegressionResult, BSONObjectID]

  type HtmlSnippetRepo = AsyncCrudRepo[HtmlSnippet, BSONObjectID]

  // experimental
//  type StudentDistRepo = DistributedRepo[Student, BSONObjectID]
//  type JsObjectDistRepo = DistributedRepo[JsObject, BSONObjectID]
}