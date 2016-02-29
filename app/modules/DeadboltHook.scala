package modules

import be.objectify.deadbolt.scala.{DeadboltExecutionContextProvider, TemplateFailureListener}
import be.objectify.deadbolt.scala.cache.HandlerCache
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import security.{CustomHandlerCacheImpl, CustomDeadboltExecutionContextProvider, AdaCustomTemplateFailureListener}

/**
  * Exposes handlers to deadbolt module
  *
  * @author Steve Chaloner (steve@objectify.be)
  */
class DeadboltHook extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[TemplateFailureListener].to[AdaCustomTemplateFailureListener],
    bind[HandlerCache].to[CustomHandlerCacheImpl],
    bind[DeadboltExecutionContextProvider].to[CustomDeadboltExecutionContextProvider]
  )
}
