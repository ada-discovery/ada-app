package runnables

import com.google.inject.Inject
import dataaccess.DataSetFormattersAndIds.JsObjectIdentity
import dataaccess.ignite.{JsonBinaryCacheAsyncCrudRepo, BinaryCacheFactory}
import dataaccess.{DescSort, AscSort, SerializableApplicationLifecycle}
import dataaccess.Criterion.CriterionInfix
import org.apache.ignite.Ignite
import dataaccess.mongo.JsonRepoFactory
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import play.api.Configuration
import scala.concurrent.duration._
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

import scala.concurrent.{Await, Future}

class TestBinaryIgnite @Inject() (ignite: Ignite, cacheFactory: BinaryCacheFactory, configuration: Configuration) extends Runnable {

  val collectionName = "data-lux_park.clinical"
  val cacheName = collectionName.replaceAll("[\\.-]", "_")

  override def run = {
    val identity = JsObjectIdentity
    val cache = cacheFactory(
      cacheName,
      Nil,
      new JsonRepoFactory(
        collectionName,
        configuration,
        new SerializableApplicationLifecycle()
      ),
      identity.of(_)
    )// new DefaultApplicationLifecycle().addStopHook
    val repo = new JsonBinaryCacheAsyncCrudRepo(cache, cacheName, ignite, identity)
    cache.loadCache(null)

    val key = BSONObjectID("577e18bc669401942c5fd71f")
    val item = repo.get(key)
    println(Await.result(item, 2 minutes))

    val allFuture = repo.find(
      criteria = Seq("purdue_test___5" #== "0"),
      projection = Seq("purdue_test___5", "sv_age"),
      limit = Some(10),
      page = Some(2),
      sort = Seq(AscSort("sv_age"))  // , DescSort("purdue_test___5")
    )
    println(Await.result(allFuture, 2 minutes).mkString("\n"))
  }
}

object TestBinaryIgnite extends GuiceBuilderRunnable[TestBinaryIgnite] with App { run }