package util

import com.google.inject.Injector
import org.ada.web.services.GuicePlayWebTestApp
import net.codingwell.scalaguice.InjectorExtensions._

object InjectorWrapper {
  private lazy val injector = GuicePlayWebTestApp(
    excludeModules = Seq("org.ada.web.security.PacSecurityModule"))
    .injector.instanceOf[Injector]

  def instanceOf[T: Manifest] = injector.instance[T]

}
