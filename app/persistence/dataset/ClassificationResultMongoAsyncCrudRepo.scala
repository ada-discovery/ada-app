package persistence.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import dataaccess.RepoTypes.DictionaryRootRepo
import dataaccess.mongo.dataset.DictionarySubordinateMongoAsyncCrudRepo
import org.incal.spark_ml.models.results.ClassificationResult
import models.ml.classification.ClassificationResult._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

import scala.concurrent.Future

class ClassificationResultMongoAsyncCrudRepo @Inject()(
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[ClassificationResult, BSONObjectID]("classificationResults", dataSetId, dictionaryRepo) {

  private val identity = ClassificationResultIdentity

  override def save(entity: ClassificationResult): Future[BSONObjectID] = {
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}