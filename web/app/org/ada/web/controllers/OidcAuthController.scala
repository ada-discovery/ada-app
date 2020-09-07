package org.ada.web.controllers

import javax.inject.Inject
import jp.t2v.lab.play2.auth.Login
import org.ada.server.services.UserManager
import org.ada.web.security.AdaAuthConfig
import org.pac4j.core.config.Config
import org.pac4j.core.profile._
import org.pac4j.play.scala._
import org.pac4j.play.store.PlaySessionStore
import play.api.Logger
import play.api.mvc._
import play.libs.concurrent.HttpExecutionContext
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class OidcAuthController @Inject() (
  val config: Config,                       // for PAC4J
  val playSessionStore: PlaySessionStore,   // for PAC4J
  override val ec: HttpExecutionContext,    // for PAC4J
  val userManager: UserManager              // for Play2 Auth (AuthConfig)
) extends Controller
    with Security[CommonProfile]            // PAC4J
    with Login                              // Play2 Auth
    with AdaAuthConfig {                    // Play2 Auth

  def oidcLogin = Secure("OidcClient") { profiles =>
    Action.async { implicit request =>
      Logger.info(s"Successful authentication for the user '${profiles.head.getUsername}' using the OIDC provider.")
      gotoLoginSucceeded(profiles.head.getUsername)
    }
  }
}
