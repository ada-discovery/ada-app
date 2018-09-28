package dataaccess.ignite

import javax.inject.{Inject, Provider, Singleton}

import org.apache.ignite.internal.IgnitionEx
import org.apache.ignite.{Ignite, Ignition}
import play.api.Configuration

@Singleton
class IgniteFactory @Inject() (configuration: Configuration) extends Provider[Ignite] {

  private val configurationFilePath = configuration.getString("ignite.conf.path").get

  override def get(): Ignite = {
//    // Create new configuration.
//    val cfg = new IgniteConfiguration()

    // Provide lifecycle bean to configuration.
//  cfg.setLifecycleBeans(new MyLifecycleBean());

    // Start Ignite node with given configuration.
    val configuration = IgnitionEx.loadConfiguration(configurationFilePath).getKey
    Ignition.getOrStart(configuration)
  }
}