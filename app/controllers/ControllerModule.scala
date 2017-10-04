package controllers

import com.google.inject.AbstractModule
import com.google.inject.assistedinject.FactoryModuleBuilder
import controllers.dataset._

class ControllerModule extends AbstractModule {

  override def configure() {
    install(new FactoryModuleBuilder()
      .implement(classOf[DataSetController], classOf[DataSetControllerImpl])
      .build(classOf[GenericDataSetControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[DictionaryController], classOf[DictionaryControllerImpl])
        .build(classOf[DictionaryControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[CategoryController], classOf[CategoryControllerImpl])
      .build(classOf[CategoryControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[FilterController], classOf[FilterControllerImpl])
      .build(classOf[FilterControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[DataViewController], classOf[DataViewControllerImpl])
      .build(classOf[DataViewControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[ClassificationRunController], classOf[ClassificationRunControllerImpl])
      .build(classOf[ClassificationRunControllerFactory]))
  }
}