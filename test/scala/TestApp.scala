package scala

import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Created by peter on 31.01.18.
  */
object TesstUtil {

  val app = {
    import scala.collection.JavaConversions.iterableAsScalaIterable
    val env = play.api.Environment.simple(mode = play.api.Mode.Dev)
    val config = play.api.Configuration.load(env)
    val modules = config.getStringList("play.modules.enabled").fold(
      List.empty[String])(l => iterableAsScalaIterable(l).toList)
    new GuiceApplicationBuilder().configure("play.modules.enabled" -> modules).build
  }
}
