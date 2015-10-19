package standalone

import javax.inject.Inject

import play.api.libs.json.Json

import scala.concurrent.duration._
import persistence.DeNoPaFirstVisitMetaTypeStatsRepo
import scala.concurrent.Await

class DeNoPaPlayground @Inject() (
    metaTypeStatsRepo : DeNoPaFirstVisitMetaTypeStatsRepo
  ) extends Runnable {

  val timeout = 120000 millis

  val truthValues = List("na", "ja", "nein", "falsch", "richtig", "fehlend")

  override def run = {
    val statsFuture = metaTypeStatsRepo.find(None, Some(Json.obj("attributeName" -> 1)))
    val enumFields = Await.result(statsFuture, timeout).filter{ item =>
      val keys = item.valueRatioMap.keySet
      (keys.size > 1 && keys.size < 15 && keys.filterNot(_.equals("NA")).exists(_.exists(_.isLetter) && !keys.forall(s => truthValues.contains(s.toLowerCase))))
    }
    enumFields.foreach(field =>
      println(s"${field.attributeName}§${field.valueRatioMap.keySet.toSeq.filterNot(_.equals("NA")).sorted.mkString("§")}")
    )
  }
}

object DeNoPaPlayground extends GuiceBuilderRunnable[DeNoPaPlayground] with App {
  override def main(args: Array[String]) = run
}