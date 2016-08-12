package dataaccess.ignite

import org.apache.ignite.configuration.IgniteConfiguration
import org.apache.ignite.{Ignite, Ignition}

object IgniteFactory {

  def apply: Ignite = {
    // Create new configuration.
    val cfg = new IgniteConfiguration();

    // Provide lifecycle bean to configuration.
    //  cfg.setLifecycleBeans(new MyLifecycleBean());

    // Start Ignite node with given configuration.
    val ignite = Ignition.start("conf/ignite-cache.xml")
    ignite
  }
}