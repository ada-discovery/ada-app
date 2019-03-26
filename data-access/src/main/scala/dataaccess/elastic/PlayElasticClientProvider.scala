package dataaccess.elastic

import javax.inject.{Inject, Provider}
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import org.elasticsearch.common.settings.Settings
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import scala.collection.JavaConversions._
import scala.concurrent.Future

class ElasticClientProviderXXX @Inject()(configuration: Configuration, lifecycle: ApplicationLifecycle) extends Provider[ElasticClient] {

  protected def shutdownHook(client: ElasticClient): Unit =
    lifecycle.addStopHook(() => Future.successful(client.close()))

  override def get(): ElasticClient = {
    val config = configuration.underlying
    val elasticConfig = config.getConfig("elastic")

    val host = elasticConfig.getString("host")
    val port = elasticConfig.getInt("port")

    val settings = Settings.settingsBuilder

    elasticConfig.entrySet().foreach { entry =>
      settings.put(entry.getKey, entry.getValue.unwrapped.toString)
    }

    val client = ElasticClient.transport(
      settings.build,
      ElasticsearchClientUri(s"elasticsearch://$host:$port")
    )

    // add a shutdown hook to the client
    shutdownHook(client)

    client
  }
}