package dataaccess.elastic

import javax.inject.{Inject, Provider}

import com.evojam.play.elastic4s.PlayElasticFactory
import com.evojam.play.elastic4s.configuration.ClusterSetup
import com.sksamuel.elastic4s.ElasticClient
import org.incal.core.dataaccess.AsyncCrudRepo

class ElasticClientProvider extends Provider[ElasticClient] {

  @Inject private var cs: ClusterSetup = _
  @Inject private var elasticFactory: PlayElasticFactory = _

  override def get(): ElasticClient =
    elasticFactory(cs)
}