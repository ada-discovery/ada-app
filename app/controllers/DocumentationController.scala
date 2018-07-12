package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.core.WebContext
import play.api.Configuration
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller, Request}
import play.twirl.api.Html
import views.html.documentation
import play.api.cache.Cached

class DocumentationController @Inject()(
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets,
    configuration: Configuration,
    cached: Cached
  ) extends Controller {

  private implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets, configuration)
  }

  def intro =
    showHtml("intro", documentation.intro()(_))

  def basic =
    showHtml("basic", documentation.basic()(_))

  def stats =
    showHtml("stats", documentation.stats()(_))

  def views =
    showHtml("views", documentation.view()(_))

  def filters =
    showHtml("filters", documentation.filters()(_))

  def ml =
    showHtml("ml", documentation.ml()(_))

  def mlClassification =
    showHtml("mlClassification", documentation.mlClassification()(_))

  def mlRegression =
    showHtml("mlRegression", documentation.mlRegression()(_))

  def mlClusterization =
    showHtml("mlClusterization", documentation.mlClusterization()(_))

  def userManagement =
    showHtml("userManagement", documentation.userManagement()(_))

  def dataSetImport =
    showHtml("dataSetImport", documentation.dataSetImport()(_))

  def technology =
    showHtml("technology", documentation.technology()(_))

  private def showHtml(
    cacheName: String,
    html: WebContext => Html
  ) = // cached(s"documentation-$cacheName") ( // TODO: introduce caching only if a user is not logged in
    Action { implicit request =>
      Ok(html(webContext))
    }
  // )
}