package org.ada.web.controllers

import javax.inject.Inject
import org.pac4j.core.client.{Clients, IndirectClient}
import org.pac4j.core.config.Config
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.credentials.Credentials
import org.pac4j.core.profile._
import org.pac4j.core.util.CommonHelper
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala._
import org.pac4j.play.store.PlaySessionStore
import play.api.libs.json.Json
import play.api.mvc._
import play.libs.concurrent.HttpExecutionContext

import scala.collection.JavaConversions._

class OidcAuthController @Inject() (val config: Config, val playSessionStore: PlaySessionStore, override val ec: HttpExecutionContext) extends Controller with Security[CommonProfile] {

  private def getProfiles(implicit request: RequestHeader): List[CommonProfile] = {
    val webContext = new PlayWebContext(request, playSessionStore)
    val profileManager = new ProfileManager[CommonProfile](webContext)
    val profiles = profileManager.getAll(true)
    asScalaBuffer(profiles).toList
  }

  def index = Secure("AnonymousClient", "csrfToken") { profiles =>
    Action { request =>
      val webContext = new PlayWebContext(request, playSessionStore)
      val sessionId = webContext.getSessionIdentifier()
      val csrfToken = webContext.getSessionAttribute(Pac4jConstants.CSRF_TOKEN).asInstanceOf[String]
      Ok(views.html.index(profiles, csrfToken, sessionId))
    }
  }

  def csrfIndex = Secure("AnonymousClient", "csrfCheck") { profiles =>
    Action { request =>
      Ok(views.html.csrf(profiles))
    }
  }

  def facebookNotProtectedIndex = Action { request =>
    Ok(views.html.notprotectedIndex(getProfiles(request)))
  }

  // secured by filter
  def facebookIndex = Action { request =>
    Ok(views.html.protectedIndex(getProfiles(request)))
  }

  def facebookAdminIndex = Secure("FacebookClient", "admin") { profiles =>
    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }

  def facebookCustomIndex = Secure("FacebookClient", "custom") { profiles =>
    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }

  def twitterIndex = Secure("TwitterClient,FacebookClient") { profiles =>
    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }

  def protectedIndex = Secure { profiles =>
    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }

  def protectedCustomIndex = Secure(null, "custom") { profile =>
    Action { request =>
      Ok(views.html.protectedIndex(profile))
    }
  }

  def formIndex = Secure("FormClient") { profiles =>
    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }

  // Setting the isAjax parameter is no longer necessary as AJAX requests are automatically detected:
  // a 401 error response will be returned instead of a redirection to the login url.
  def formIndexJson = Secure("FormClient") { profiles =>
    Action { request =>
      val content = views.html.protectedIndex.render(profiles)
      val json = Json.obj("content" -> content.toString())
      Ok(json).as("application/json")
    }
  }

  def basicauthIndex = Secure("IndirectBasicAuthClient") { profiles =>
    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }

  def dbaIndex = Secure("DirectBasicAuthClient,ParameterClient") { profiles =>
    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }

  def casIndex = Secure("CasClient") { profiles =>
    Action { request => {
      val profile = profiles.get(0)
      val service = "http://localhost:8080/proxiedService"
      var proxyTicket: String = null
      if (profile.isInstanceOf[CasProxyProfile]) {
        val proxyProfile = profile.asInstanceOf[CasProxyProfile]
        proxyTicket = proxyProfile.getProxyTicketFor(service)
      }
      Ok(views.html.casProtectedIndex.render(profile, service, proxyTicket))
      //Ok(views.html.protectedIndex(profiles))
    }
    }
  }
  
  def samlIndex = Secure("SAML2Client") { profiles =>
    Action { request =>
      Ok(views.html.protectedIndex(profiles))
    }
  }

  def oidcIndex = Secure("OidcClient") { profiles =>
    Action { request =>

      println("GOT: " + profiles.map(_.toString).mkString("\n"))

      Ok(views.html.protectedIndex(profiles))
    }
  }

  // secured by filter
  def restJwtIndex = Action { request =>
    Ok(views.html.protectedIndex(getProfiles(request)))
  }

  def loginForm = Action { request =>
    val formClient = config.getClients.findClient("FormClient").asInstanceOf[FormClient]
    Ok(views.html.loginForm.render(formClient.getCallbackUrl))
  }

  def jwt = Action { request =>
    val profiles = getProfiles(request)
    val generator = new JwtGenerator[CommonProfile](new SecretSignatureConfiguration("12345678901234567890123456789012"))
    var token: String = ""
    if (CommonHelper.isNotEmpty(profiles)) {
      token = generator.generate(profiles.get(0))
    }
    Ok(views.html.jwt.render(token))
  }

  def forceLogin = Action { request =>
    val context: PlayWebContext = new PlayWebContext(request, playSessionStore)
    val client = config.getClients.findClient(context.getRequestParameter(Clients.DEFAULT_CLIENT_NAME_PARAMETER)).asInstanceOf[IndirectClient[Credentials,CommonProfile]]
    Redirect(client.getRedirectAction(context).getLocation)
  }
}
