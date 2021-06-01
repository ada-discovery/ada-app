package org.ada.server.services

import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import play.api.{Application, inject}
import play.api.inject.guice.GuiceApplicationBuilder

object GuicePlayTestApp {

  def apply(moduleNames: Seq[String] = Nil): Application = {
    val env = play.api.Environment.simple()
    val config = play.api.Configuration.load(env)

    val modules =
      if (moduleNames.nonEmpty) {
        moduleNames
      } else {
        import scala.collection.JavaConversions.iterableAsScalaIterable
        config.getStringList("play.modules.enabled").fold(
          List.empty[String])(l => iterableAsScalaIterable(l).filterNot(_ == "org.ada.web.security.PacSecurityModule").toList)
      }
    new GuiceApplicationBuilder()
      .overrides(inject.bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore]))
      .configure("play.modules.enabled" -> modules)
      .configure(("mongodb.uri", "mongodb://localhost:27017/ada")).build
  }
}