package runnables

import play.api.inject.guice.GuiceApplicationBuilder

class GuiceBuilderRunnable[T <: Runnable](implicit ev: Manifest[T]) {

  val app = {
    import scala.collection.JavaConversions.iterableAsScalaIterable
    val env = play.api.Environment.simple(mode = play.api.Mode.Dev)
    val config = play.api.Configuration.load(env)
    val modules = config.getStringList("play.modules.enabled").fold(
      List.empty[String])(l => iterableAsScalaIterable(l).toList)
    new GuiceApplicationBuilder().configure("play.modules.enabled" -> modules).build
  }

  def run = {
    app.injector.instanceOf[T].run
    app.stop()
  }
}