package runnables.mpower

import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.Sink
import org.ada.server.models.{Field, FieldTypeId, StorageType}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsNumber, Json}
import org.incal.core.runnables.InputFutureRunnable
import org.ada.server.services.DataSetService

import scala.io.Source
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class ImportMPowerIndividualFeatureScores @Inject() (
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService
) extends InputFutureRunnable[ImportMPowerIndividualFeatureScoresSpec] {

  case class FeatureInfo(
    SubmissionId: Int,
    Name: String,
    Rank_Unbiased_Subset: Int
  )

  implicit val featureInfoFormat = Json.format[FeatureInfo]

  private val fieldNames = Seq("SubmissionId", "Name", "Rank_Unbiased_Subset")

  private val delimiter = ","

  private val newAUCField = Field("Individual_AUROC", Some("Individual AUROC"), FieldTypeId.Double)

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(
    input: ImportMPowerIndividualFeatureScoresSpec
  ) = {
    val dsa = dsaf(input.featureDataSetId).get
    val individualAucsMap = loadFromFile(input.fileName)

    for {
      // old fields
      fields <- dsa.fieldRepo.find()

      // register a new data set
      newDsa <- dataSetService.register(dsa, input.newDataSetId, input.newDataSetName, StorageType.ElasticSearch)

      // update dictionary
      _ <- dataSetService.updateDictionaryFields(newDsa.fieldRepo, fields ++ Seq(newAUCField), false, true)

      // delete the old results (if any)
      _ <- newDsa.dataSetRepo.deleteAll

      // load the features infos
      featureInfosStream <- dsa.dataSetRepo.findAsStream()

      // save new feature infos (with auc)
      _ <- featureInfosStream
        .grouped(input.processingBatchSize)
        .buffer(10, OverflowStrategy.backpressure)
        .mapAsync(1) { jsons =>
          val newJsons = jsons.map { json =>
            val featureInfo = json.as[FeatureInfo]
            val auc = individualAucsMap.get((featureInfo.Rank_Unbiased_Subset, featureInfo.Name)).getOrElse(
              throw new IllegalArgumentException(s"No row found for a rank ${featureInfo.Rank_Unbiased_Subset} and feature name ${featureInfo.Name}.")
            )
            json.+(newAUCField.name -> JsNumber(auc))
          }
          newDsa.dataSetRepo.save(newJsons)
        }.runWith(Sink.ignore)

      // get the old views
      views <- dsa.dataViewRepo.find()

      // save the views to the new data set
      _ <- {
        val newViews = views.map(_.copy(timeCreated = new java.util.Date(), _id = None))
        newDsa.dataViewRepo.save(newViews)
      }
    } yield
      ()
  }

  private def loadFromFile(fileName: String): Map[(Int, String), Double] = {
    val lines = Source.fromFile(fileName).getLines()

    val header = lines.next

    lines.map { line =>
      val parts = line.replaceAllLiterally("\"","").split(delimiter, -1)

      val rankFeatureName = parts(1).trim.substring(11).split("_", 2)
      val rank = rankFeatureName(0).toInt
      val featureName = rankFeatureName(1)

      val adjustedRank = if (rank > 14) rank + 1 else rank

      val auc = parts(2).trim.toDouble

      (adjustedRank, featureName) -> auc
    }.toMap
  }

  override def inputType = typeOf[ImportMPowerIndividualFeatureScoresSpec]
}

case class ImportMPowerIndividualFeatureScoresSpec(
  featureDataSetId: String,
  newDataSetId: String,
  newDataSetName: String,
  fileName: String,
  processingBatchSize: Int
)
