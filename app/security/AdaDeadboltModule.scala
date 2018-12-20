package security

import be.objectify.deadbolt.scala.cache.HandlerCache
import be.objectify.deadbolt.scala.{DeadboltExecutionContextProvider, DeadboltModule, TemplateFailureListener}
import play.api.{Configuration, Environment}

/**
  * Extension of Deadbolt module with some custom/Ada stuff
  */
class AdaDeadboltModule extends DeadboltModule {

  override def bindings(environment: Environment, configuration: Configuration) =

    super.bindings(environment, configuration) ++ Seq(
      {
        configuration.getString("play.http.context").foreach(router.RoutesPrefix.setPrefix)
        bind[TemplateFailureListener].to[AdaTemplateFailureListener]
      },
      bind[HandlerCache].to[CustomHandlerCacheImpl],
      bind[DeadboltExecutionContextProvider].to[AdaDeadboltExecutionContextProvider]
    )
}
