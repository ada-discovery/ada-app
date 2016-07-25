package runnables

import com.google.inject.Inject
import models.security.CustomUser
import models.security.CustomUser._
import org.apache.ignite.cache.query.{ScanQuery}
import org.apache.ignite.lang.IgniteBiPredicate
import persistence.{AsyncCrudRepo, MongoAsyncCrudRepo}
import play.api.inject.ApplicationLifecycle
import play.modules.reactivemongo.DefaultReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import javax.cache.configuration.Factory
import services.ignite.CacheFactory
import play.api.Configuration

import scala.concurrent.Future

class TestIgnite @Inject() (cacheFactory: CacheFactory, configuration: Configuration, applicationLifecycle: ApplicationLifecycle) extends Runnable {

  override def run = {
    val cache = cacheFactory(new RepoFactory(configuration, new SerializableApplicationLifecycle()), CustomUser.UserIdentity.of) // new DefaultApplicationLifecycle().addStopHook
    cache.loadCache(null)

    val userKey = BSONObjectID("577e512f960000c100f7e8f2")

    val cursor = cache.query(new ScanQuery[BSONObjectID, CustomUser](new IgniteBiPredicate[BSONObjectID, CustomUser] {
      override def apply(key: BSONObjectID, e2: CustomUser): Boolean = key.equals(userKey)
    }))

    println(cursor.getAll)

    val user = cache.get(userKey)
    println(user)
  }
}

class RepoFactory(configuration: Configuration, applicationLifecycle: ApplicationLifecycle) extends Factory[AsyncCrudRepo[CustomUser, BSONObjectID]] {

  override def create(): AsyncCrudRepo[CustomUser, BSONObjectID] = {
    val repo = new MongoAsyncCrudRepo[CustomUser, BSONObjectID]("users")
//    val repo = new MongoAsyncCrudRepo[CustomUser, BSONObjectID]("users", UserIdentity,
//      serializeFormat(UserFormat.reads, UserFormat.writes),
//      serializeFormat(BSONObjectIDFormat.reads, BSONObjectIDFormat.writes))
    repo.reactiveMongoApi = new DefaultReactiveMongoApi(configuration, applicationLifecycle)
    repo
  }
}

class SerializableApplicationLifecycle() extends ApplicationLifecycle with Serializable { // addStopHookX: (() => Future[Unit]) => Unit
  override def addStopHook(hook: () => Future[Unit]): Unit = () // addStopHookX(hook)
}

object TestIgnite extends GuiceBuilderRunnable[TestIgnite] with App { run }
