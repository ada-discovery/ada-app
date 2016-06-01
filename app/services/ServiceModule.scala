package services

import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder

class ServiceModule extends AbstractModule {

  override def configure() = {
    install(new FactoryModuleBuilder()
      .implement(classOf[SynapseService], classOf[SynapseServiceWSImpl])
      .build(classOf[SynapseServiceFactory]))
  }
}