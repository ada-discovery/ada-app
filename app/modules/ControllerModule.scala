package modules

import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import controllers.dataset.{DictionaryControllerFactory, DictionaryController, DictionaryControllerImpl}
import persistence.dataset.{DataSetAccessorMongoFactory, DataSetAccessorFactory}
import play.api.inject.{Binding, Module}

class ControllerModule extends AbstractModule {

  override def configure() {
    install(new FactoryModuleBuilder()
      .implement(classOf[DictionaryController], classOf[DictionaryControllerImpl])
        .build(classOf[DictionaryControllerFactory]))
  }
}