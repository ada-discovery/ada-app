package persistence

import models.{DataSetImport, Message, Translation}
import org.ada.server.models._
import models.workspace.Workspace
import reactivemongo.bson.BSONObjectID
import org.ada.server.models.HtmlSnippet
import models.ml.regression.RegressionResult
import models.ml.unsupervised.UnsupervisedLearning
import org.incal.core.dataaccess.{AsyncCrudRepo, AsyncStreamRepo}
import org.incal.spark_ml.models.classification.Classifier
import org.incal.spark_ml.models.regression.Regressor
import org.incal.spark_ml.models.result._

/**
 * Common repo type shortcuts
 */
object RepoTypes {
//  type JsObjectCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]
  type TranslationRepo = AsyncCrudRepo[Translation, BSONObjectID]

  type MessageRepo = AsyncStreamRepo[Message, BSONObjectID]
  type UserSettingsRepo = AsyncCrudRepo[Workspace, BSONObjectID]

  type DataSetImportRepo = AsyncCrudRepo[DataSetImport, BSONObjectID]

  type ClassifierRepo = AsyncCrudRepo[Classifier, BSONObjectID]
  type RegressorRepo = AsyncCrudRepo[Regressor, BSONObjectID]
  type UnsupervisedLearningRepo = AsyncCrudRepo[UnsupervisedLearning, BSONObjectID]

  type ClassificationResultRepo = AsyncCrudRepo[ClassificationResult, BSONObjectID]
  type StandardClassificationResultRepo = AsyncCrudRepo[StandardClassificationResult, BSONObjectID]
  type TemporalClassificationResultRepo = AsyncCrudRepo[TemporalClassificationResult, BSONObjectID]

  type RegressionResultRepo = AsyncCrudRepo[RegressionResult, BSONObjectID]
  type StandardRegressionResultRepo = AsyncCrudRepo[StandardRegressionResult, BSONObjectID]
  type TemporalRegressionResultRepo = AsyncCrudRepo[TemporalRegressionResult, BSONObjectID]

  type HtmlSnippetRepo = AsyncCrudRepo[HtmlSnippet, BSONObjectID]

  // experimental
//  type StudentDistRepo = DistributedRepo[Student, BSONObjectID]
//  type JsObjectDistRepo = DistributedRepo[JsObject, BSONObjectID]
}