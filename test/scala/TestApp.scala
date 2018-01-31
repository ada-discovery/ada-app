package scala

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

object TestApp {

  val apply: Application = {
    import scala.collection.JavaConversions.iterableAsScalaIterable
    val env = play.api.Environment.simple(mode = play.api.Mode.Test)
    val config = play.api.Configuration.load(env)
    val modules = config.getStringList("play.modules.enabled").fold(
      List.empty[String])(l => iterableAsScalaIterable(l).toList)
    new GuiceApplicationBuilder().configure("play.modules.enabled" -> modules).build
  }
}
