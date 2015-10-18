package standalone

import javax.inject.Inject

import persistence.{DeNoPaBaselineMetaTypeStatsRepo, DeNoPaBaselineRepo}
import play.api.libs.json.{JsNull, JsValue, JsObject, Json}
import scala.concurrent.{Future, Await}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class InferTypeDeNoPaBaseline @Inject() (
    baselineRepo: DeNoPaBaselineRepo,
    baselineMetaTypeStatsRepo : DeNoPaBaselineMetaTypeStatsRepo
  ) extends InferTypeDeNoPa with Runnable {

  override def run = {
    // get the keys (attributes)
    val uniqueCriteria = Some(Json.obj("Line_Nr" -> "1"))
    val keysFuture = baselineRepo.find(uniqueCriteria).map(_.head.keys)

    // clean up the collection
    val deleteFuture = baselineMetaTypeStatsRepo.deleteAll
    Await.result(deleteFuture, timeout)

    Await.result(keysFuture, timeout).filter(_ != "_id").par.foreach { key =>
      // get all the values for a given key
      val valuesFuture = baselineRepo.find(None, None, Some(Json.obj(key -> 1))).map(_.map(item => item.value.get(key).get))
      // collect type stats
      println(key)
      val counts = new MetaTypeCounts
      val values = Await.result(valuesFuture, timeout)
      val fullCount = values.size
      values.foreach { value =>
        inferType(value.as[JsValue], counts)
      }
      val stats = createStats(key, counts, fullCount)
      baselineMetaTypeStatsRepo.save(stats)
    }
  }
}

object InferTypeDeNoPaBaseline extends GuiceBuilderRunnable[InferTypeDeNoPaBaseline] with App {
  override def main(args: Array[String]) = run
}