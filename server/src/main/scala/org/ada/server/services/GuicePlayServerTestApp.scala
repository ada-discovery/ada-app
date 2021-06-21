package org.ada.server.services

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

object GuicePlayServerTestApp extends GuicePlayTestApp {

  override def apply(moduleNames: Seq[String] = Nil, excludeModules: Seq[String] = Nil): Application = {
    new GuiceApplicationBuilder()
      .configure("play.modules.enabled" -> getModules(moduleNames, excludeModules)).build
  }
}