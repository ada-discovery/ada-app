package modules

import be.objectify.deadbolt.scala.{DeadboltExecutionContextProvider, TemplateFailureListener}
import be.objectify.deadbolt.scala.cache.HandlerCache
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import security.{CustomHandlerCacheImpl, AdaDeadboltExecutionContextProvider, AdaTemplateFailureListener}

/**
  * Exposes handlers to deadbolt module
  *
  * @author Steve Chaloner (steve@objectify.be)
  */
class DeadboltHook extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[TemplateFailureListener].to[AdaTemplateFailureListener],
    bind[HandlerCache].to[CustomHandlerCacheImpl],
    bind[DeadboltExecutionContextProvider].to[AdaDeadboltExecutionContextProvider]
  )
}
