package org.ada.web.security

import com.google.inject.{AbstractModule, Provides}
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}

class PacSecurityModule (environment: Environment, configuration: Configuration) extends AbstractModule {

  val adaBaseUrl: String = configuration.getString("oidc.adaBaseUrl").get

  override def configure(): Unit = {
    bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])

    // callback
    val callbackController = new CallbackController()
    callbackController.setDefaultUrl("/?defaulturlafterlogout")
    callbackController.setMultiProfile(true)
    bind(classOf[CallbackController]).toInstance(callbackController)

    // logout
    val logoutController = new LogoutController()
    logoutController.setDefaultUrl(adaBaseUrl)
    logoutController.setCentralLogout(true)
    bind(classOf[LogoutController]).toInstance(logoutController)

  }


  @Provides
  def provideOidcClient: OidcClient[OidcProfile] = {
    val oidcConfiguration = new OidcConfiguration()
    oidcConfiguration.setClientId(configuration.getString("oidc.clientId").get)
    oidcConfiguration.setSecret(configuration.getString("oidc.secret").get)
    oidcConfiguration.setClientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
    oidcConfiguration.setPreferredJwsAlgorithm(JWSAlgorithm.RS256)
    oidcConfiguration.setDiscoveryURI(configuration.getString("oidc.discoveryURI").get)
    val oidcClient = new OidcClient[OidcProfile](oidcConfiguration)
    oidcClient
  }

  @Provides
  def provideConfig(oidcClient: OidcClient[OidcProfile]) : Config = {
    val clients = new Clients(s"$adaBaseUrl/oidc/callback", oidcClient)
    val config = new Config(clients)
    config.setHttpActionAdapter(new CustomHttpActionAdapter)
    config
  }

}
