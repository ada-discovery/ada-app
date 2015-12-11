package standalone

import javax.inject.{Named, Inject}

import persistence.RepoSynchronizer
import persistence.RepoTypeRegistry._
import play.api.libs.json.{JsValue, Json}
import scala.concurrent.{Future, Await}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class InferTypeDeNoPaBaseline @Inject() (
    @Named("DeNoPaBaselineRepo") dataRepo: JsObjectCrudRepo,
    @Named("DeNoPaBaselineMetaTypeStatsRepo") typeStatsRepo : MetaTypeStatsRepo
  ) extends InferTypeDeNoPa with Runnable {

  private val syncDataRepo = RepoSynchronizer(dataRepo, timeout)
  private val syncTypeStatsRepo = RepoSynchronizer(typeStatsRepo, timeout)

  override def run = {
    // clean up the collection
    syncTypeStatsRepo.deleteAll

    // get the keys (attributes)
    val uniqueCriteria = Some(Json.obj("Line_Nr" -> "1"))
    val fieldNames = syncDataRepo.find(uniqueCriteria).head.keys

    fieldNames.filter(_ != "_id").par.foreach { field =>
      println(field)
      // get all the values for a given field
      val values = syncDataRepo.find(None, None, Some(Json.obj(field -> 1))).map(item => item.value.get(field).get)

      // collect type stats
      val counts = new MetaTypeCounts
      val fullCount = values.size
      values.foreach { value =>
        inferType(value.as[JsValue], counts)
      }
      val stats = createStats(field, counts, fullCount)
      typeStatsRepo.save(stats)
    }
  }
}

object InferTypeDeNoPaBaseline extends GuiceBuilderRunnable[InferTypeDeNoPaBaseline] with App {
  run
}