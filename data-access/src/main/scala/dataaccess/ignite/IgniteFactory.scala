package dataaccess.ignite

import org.apache.ignite.configuration.IgniteConfiguration
import org.apache.ignite.internal.IgnitionEx
import org.apache.ignite.{Ignite, Ignition}

object IgniteFactory {

  private val configurationFilePath = "conf/ignite-cache.xml"

  def apply: Ignite = {
    // Create new configuration.
    val cfg = new IgniteConfiguration();

    // Provide lifecycle bean to configuration.
    //  cfg.setLifecycleBeans(new MyLifecycleBean());

    // Start Ignite node with given configuration.
    val configuration = IgnitionEx.loadConfiguration(configurationFilePath).getKey
    val ignite = Ignition.getOrStart(configuration)
    ignite
  }
}