package services

import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import dataaccess.ignite.IgniteFactory
import net.codingwell.scalaguice.ScalaModule
import org.apache.ignite.Ignite

class ServiceModule extends ScalaModule {

  override def configure = {
    bind[Ignite].toInstance(IgniteFactory.apply)

    install(new FactoryModuleBuilder()
      .implement(classOf[SynapseService], classOf[SynapseServiceWSImpl])
      .build(classOf[SynapseServiceFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[RedCapService], classOf[RedCapServiceWSImpl])
      .build(classOf[RedCapServiceFactory]))
  }
}