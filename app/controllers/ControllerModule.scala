package controllers

import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import controllers.dataset.{DictionaryController, DictionaryControllerFactory, DictionaryControllerImpl}

class ControllerModule extends AbstractModule {

  override def configure() {
    install(new FactoryModuleBuilder()
      .implement(classOf[DictionaryController], classOf[DictionaryControllerImpl])
        .build(classOf[DictionaryControllerFactory]))
  }
}