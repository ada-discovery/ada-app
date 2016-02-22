package modules

import controllers.DataSetDispatcher
import controllers.denopa.DeNoPaBaselineController
import controllers.luxpark.LuxParkController
import play.api.{Environment, ApplicationLoader, Configuration}
import play.api.inject._
import play.api.inject.guice._

import scala.reflect.ClassTag

class AdaApplicationLoader extends GuiceApplicationLoader() {

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    val builder = super.builder(context)

    def get[T: ClassTag] = builder.injector.instanceOf[T]
    val dataSetControllers = Seq(get[LuxParkController], get[DeNoPaBaselineController])

    builder.overrides(new Module {
      override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
        bind[DataSetDispatcher].toInstance(new DataSetDispatcher(
          dataSetControllers.map(controller => (controller.dataSetName, controller))
        ))
      )
    })
  }
}