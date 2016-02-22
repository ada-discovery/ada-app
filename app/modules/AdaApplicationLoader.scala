package modules

import controllers.{DictionaryDispatcher, DataSetDispatcher}
import controllers.denopa._
import controllers.luxpark.{LuxParkDictionaryController, LuxParkController}
import play.api.{Environment, ApplicationLoader, Configuration}
import play.api.inject._
import play.api.inject.guice._

import scala.reflect.ClassTag

class AdaApplicationLoader extends GuiceApplicationLoader() {

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    val builder = super.builder(context)

    def get[T: ClassTag] = builder.injector.instanceOf[T]
    val dataSetControllers = Seq(
      get[LuxParkController],
      get[DeNoPaBaselineController],
      get[DeNoPaFirstVisitController],
      get[DeNoPaCuratedBaselineController],
      get[DeNoPaCuratedFirstVisitController]
    )
    val dictionaryControllers = Seq(
      get[LuxParkDictionaryController],
      get[DeNoPaBaselineDictionaryController],
      get[DeNoPaFirstVisitDictionaryController],
      get[DeNoPaCuratedBaselineDictionaryController],
      get[DeNoPaCuratedFirstVisitDictionaryController]
    )

    builder.overrides(new Module {
      override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
        bind[DataSetDispatcher].toInstance(new DataSetDispatcher(
          dataSetControllers.map(controller => (controller.dataSetId, controller))
        )),
        bind[DictionaryDispatcher].toInstance(new DictionaryDispatcher(
          dictionaryControllers.map(controller => (controller.dataSetId, controller))
        ))
      )
    })
  }
}