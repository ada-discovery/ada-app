package runnables.luxpark

import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.ada.server.dataaccess.JsonCrudRepoExtra._
import org.ada.server.dataaccess.StreamSpec
import org.ada.server.AdaException
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.incal.core.dataaccess.Criterion._
import org.incal.core.FutureRunnable
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.JsNumber

import scala.concurrent.ExecutionContext.Implicits.global

class CopyTrendVisitsWithIncrement @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private val sourceDataSetId = "trend.clinical_visit"
  private val targetDataSetId = "trend.clinical_visit_copied_visits"
  private val visitFieldName = "redcap_event_name"

  //  "0":"basis_arm_1"
  //  "1":"bl_arm_1"
  //  "2":"fu1_arm_1"
  //  "3":"fu2_arm_1"
  //  "4":"fu3_arm_1"
  //  "5":"fu4_arm_1"

  private val newVisitLabels = Map(
    6 -> "fu5_arm_1",
    7 -> "fu6_arm_1",
    8 -> "fu7_arm_1",
    9 -> "fu8_arm_1",
    10 -> "fu9_arm_1"
  )

  private val visitMap =  Map(
    1 -> 6,
    2 -> 7,
    3 -> 8,
    4 -> 9,
    5 -> 10
  )

  override def runAsFuture = {
    val sourceDsa = dsaf(sourceDataSetId).get
    val targetDsa = dsaf(targetDataSetId).get

    for {
      inputSource <- sourceDsa.dataSetRepo.findAsStream(Seq(visitFieldName #-> visitMap.keys.toSeq))

      remappedVisitSource = inputSource.map { json =>
        val visit = (json \ visitFieldName).as[Int]
        val newVisit = visitMap.getOrElse(visit, throw new AdaException(s"Visit $visit not found in the map."))

        json.-(JsObjectIdentity.name).+(visitFieldName, JsNumber(newVisit))
      }

      _ <- targetDsa.dataSetRepo.saveAsStream(remappedVisitSource, StreamSpec())

      visitField <- targetDsa.fieldRepo.get(visitFieldName).map(_.get)

      _ <- {
        val mergedEnumValues = visitField.numValues.get ++ newVisitLabels.map { case (from, to) => (from.toString, to) }
        targetDsa.fieldRepo.save(visitField.copy(numValues = Some(mergedEnumValues)))
      }
    } yield
      ()
  }
}
