package scala.org.ada.server.services

import com.google.inject.Injector
import org.ada.server.services.GuicePlayServerTestApp
import net.codingwell.scalaguice.InjectorExtensions._
import org.ada.web.services.GuicePlayWebTestApp

/**
 * Temporary injector wrapper to be used for testing until play has been factored out of ada-server
 */
object InjectorWrapper {
  private lazy val injector = GuicePlayWebTestApp(
    excludeModules = Seq("org.ada.web.security.PacSecurityModule"))
    .injector.instanceOf[Injector]

  def instanceOf[T: Manifest] = injector.instance[T]
}
