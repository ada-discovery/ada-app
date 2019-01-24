package persistence.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import dataaccess.RepoTypes.DictionaryRootRepo
import dataaccess.mongo.dataset.DictionarySubordinateMongoAsyncCrudRepo
import org.incal.spark_ml.models.result.RegressionResult
import models.ml.regression.RegressionResult._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

import scala.concurrent.Future

class RegressionResultMongoAsyncCrudRepo @Inject()(
    @Assisted dataSetId : String,
    dictionaryRepo: DictionaryRootRepo
  ) extends DictionarySubordinateMongoAsyncCrudRepo[RegressionResult, BSONObjectID]("regressionResults", dataSetId, dictionaryRepo) {

  private val identity = RegressionResultIdentity

  override def save(entity: RegressionResult): Future[BSONObjectID] = {
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}