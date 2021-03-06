package runnables.db

import com.google.inject.Inject
import org.ada.server.dataaccess.ignite.{BinaryCacheFactory, JsonBinaryCacheAsyncCrudRepo}
import org.ada.server.dataaccess.mongo.MongoJsonRepoFactory
import org.ada.server.dataaccess.SerializableApplicationLifecycle
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.apache.ignite.Ignite
import org.incal.core.dataaccess.AscSort
import org.incal.core.dataaccess.Criterion._
import play.api.Configuration
import reactivemongo.bson.BSONObjectID
import org.incal.play.GuiceRunnableApp

import scala.concurrent.Await
import scala.concurrent.duration._

class TestBinaryIgnite @Inject() (ignite: Ignite, cacheFactory: BinaryCacheFactory, configuration: Configuration) extends Runnable {

  val collectionName = "data-lux_park.clinical"
  val cacheName = collectionName.replaceAll("[\\.-]", "_")

  override def run = {
    val identity = JsObjectIdentity
    val cache = cacheFactory(
      cacheName,
      Nil,
      new MongoJsonRepoFactory(
        collectionName,
        false,
        configuration,
        new SerializableApplicationLifecycle()
      ),
      identity.of(_)
    )// new DefaultApplicationLifecycle().addStopHook
    val repo = new JsonBinaryCacheAsyncCrudRepo(cache, cacheName, ignite, identity)
    cache.loadCache(null)

    val key = BSONObjectID.parse("577e18bc669401942c5fd71f").get
    val item = repo.get(key)
    println(Await.result(item, 2 minutes))

    val allFuture = repo.find(
      criteria = Seq("purdue_test___5" #== "0"),
      projection = Seq("purdue_test___5", "sv_age"),
      limit = Some(10),
      skip = Some(19),
      sort = Seq(AscSort("sv_age"))  // , DescSort("purdue_test___5")
    )
    println(Await.result(allFuture, 2 minutes).mkString("\n"))
  }
}

object TestBinaryIgnite extends GuiceRunnableApp[TestBinaryIgnite]