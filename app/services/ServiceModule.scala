package services

import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import runnables.{ImportDataSetFactory, ImportDataSet}

class ServiceModule extends AbstractModule {

  override def configure() {
    // TODO: find a better place for this
    install(new FactoryModuleBuilder()
      .implement(classOf[Runnable], classOf[ImportDataSet])
      .build(classOf[ImportDataSetFactory]))
  }
}