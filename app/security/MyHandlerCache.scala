package security

import javax.inject.Singleton

import be.objectify.deadbolt.scala.{HandlerKey, DeadboltHandler}
import be.objectify.deadbolt.scala.cache.HandlerCache

/**
  * Container and hook for deadbolt handlers
  *
  */
@Singleton
class MyHandlerCache extends HandlerCache {

  val defaultHandler: DeadboltHandler = new MyDeadboltHandler
  val userlessHandler: DeadboltHandler = new MyUserlessDeadboltHandler
  val alternativeDynamicResourceHandler: DeadboltHandler = new MyDeadboltHandler(Some(MyAlternativeDynamicResourceHandler))

  val handlers: Map[Any, DeadboltHandler] = Map(HandlerKeys.defaultHandler -> defaultHandler,
                                                HandlerKeys.altHandler -> alternativeDynamicResourceHandler,
                                                HandlerKeys.userlessHandler -> userlessHandler)

  override def apply(): DeadboltHandler = defaultHandler

  override def apply(handlerKey: HandlerKey): DeadboltHandler = handlers(handlerKey)
}
