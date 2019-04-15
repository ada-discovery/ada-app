package models.ml.unsupervised

import reactivemongo.bson.BSONObjectID
import java.util.Date
import reactivemongo.play.json.BSONFormats._
import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.json.{EnumFormat, ManifestedFormat, SubTypeFormat}
import play.api.libs.json.{Format, Json}

abstract class UnsupervisedLearning {
  val _id: Option[BSONObjectID]
  val name: Option[String]
  val createdById: Option[BSONObjectID]
  val timeCreated: Date
}

object KMeansInitMode extends Enumeration {
  val random = Value("random")
  val parallel = Value("k-means||")
}

case class KMeans(
  _id: Option[BSONObjectID],
  k: Int,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  seed: Option[Long] = None,
  initMode: Option[KMeansInitMode.Value] = None,
  initSteps: Option[Int] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends UnsupervisedLearning

object LDAOptimizer extends Enumeration {
  val online = Value("online")
  val em = Value("em")
}

case class LDA(
  _id: Option[BSONObjectID],
  k: Int,
  maxIteration: Option[Int] = None,
  seed: Option[Long] = None,
  checkpointInterval: Option[Int] = None,
  docConcentration: Option[Seq[Double]] = None,
  topicConcentration: Option[Double] = None,
  optimizer: Option[LDAOptimizer.Value] = None,
  learningOffset: Option[Double] = None,
  learningDecay: Option[Double] = None,
  subsamplingRate: Option[Double] = None,
  optimizeDocConcentration: Option[Boolean] = None,
  keepLastCheckpoint: Option[Boolean] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends UnsupervisedLearning

case class BisectingKMeans(
  _id: Option[BSONObjectID],
  k: Int,
  maxIteration: Option[Int] = None,
  seed: Option[Long] = None,
  minDivisibleClusterSize: Option[Double] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends UnsupervisedLearning

case class GaussianMixture(
  _id: Option[BSONObjectID],
  k: Int,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  seed: Option[Long] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends UnsupervisedLearning

object UnsupervisedLearning {
  implicit val KMeansInitModeEnumTypeFormat = EnumFormat(KMeansInitMode)
  implicit val LDAOptimizerEnumTypeFormat = EnumFormat(LDAOptimizer)

  implicit val unsupervisedLearningFormat: Format[UnsupervisedLearning] = new SubTypeFormat[UnsupervisedLearning](
    Seq(
      ManifestedFormat(Json.format[KMeans]),
      ManifestedFormat(Json.format[LDA]),
      ManifestedFormat(Json.format[BisectingKMeans]),
      ManifestedFormat(Json.format[GaussianMixture])
    )
  )

  implicit object UnsupervisedLearningIdentity extends BSONObjectIdentity[UnsupervisedLearning] {
    def of(entity: UnsupervisedLearning): Option[BSONObjectID] = entity._id

  protected def set(entity: UnsupervisedLearning, id: Option[BSONObjectID]) =
    entity match {
      case x: KMeans => x.copy(_id = id)
      case x: LDA => x.copy(_id = id)
      case x: BisectingKMeans => x.copy(_id = id)
      case x: GaussianMixture => x.copy(_id = id)
    }
  }
}