package modules

import controllers.dataset._
import models.DataSetId
import play.api.{Environment, ApplicationLoader, Configuration}
import play.api.inject._
import play.api.inject.guice._

import scala.reflect.ClassTag

@Deprecated
class AdaApplicationLoader extends GuiceApplicationLoader() {

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    val builder = super.builder(context)
//    val injector = build.injector
//
//    def get[T: ClassTag] = injector.instanceOf[T]
//
//    val dscf = get[DataSetControllerFactory]
//    val dcf = get[DictionaryControllerFactory]
//
//    val dataSetControllers = DataSetId.values.map(id => dscf(id.toString)).flatten
//    val dictionaryControllers = dataSetControllers.map(controller => dcf(controller.dataSetId))
//
//    build.overrides(new Module {
//      override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
//        bind[DataSetDispatcher].toInstance(new DataSetDispatcher(
//          dataSetControllers.map(controller => (controller.dataSetId, controller))
//        )),
//        bind[DictionaryDispatcher].toInstance(new DictionaryDispatcher(
//          dictionaryControllers.map(controller => (controller.dataSetId, controller))
//        ))
//      )
//    })
    builder
  }
}