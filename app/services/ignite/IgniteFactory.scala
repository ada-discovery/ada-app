package services.ignite

import javax.cache.configuration.FactoryBuilder

import models.Student
import org.apache.ignite.cache.store.CacheStore
import org.apache.ignite.cache.{CacheAtomicityMode, CacheMode}
import org.apache.ignite.{Ignite, Ignition}
import org.apache.ignite.configuration.{CacheConfiguration, IgniteConfiguration}
import persistence.AsyncCrudRepo

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