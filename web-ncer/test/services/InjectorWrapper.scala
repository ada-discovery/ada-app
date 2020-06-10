package services

import com.google.inject.Injector
import net.codingwell.scalaguice.InjectorExtensions._
import org.ada.server.services.GuicePlayTestApp

/**
 * Temporary injector wrapper to be used for testing until play has been factored out of ada-server
 */
object InjectorWrapper {
  private lazy val injector = GuicePlayTestApp().injector.instanceOf[Injector]
  def instanceOf[T: Manifest] = injector.instance[T]
}
