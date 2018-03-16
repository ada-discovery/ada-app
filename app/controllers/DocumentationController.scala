package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.core.WebContext
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller, Request}
import play.twirl.api.Html
import views.html.documentation

class DocumentationController @Inject()(messagesApi: MessagesApi, webJarAssets: WebJarAssets) extends Controller {

  private implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets)
  }

  def intro = showHtml(documentation.intro()(_))

  def basic = showHtml(documentation.basic()(_))

  def stats = showHtml(documentation.stats()(_))

  def views = showHtml(documentation.view()(_))

  def filters = showHtml(documentation.filters()(_))

  def ml = showHtml(documentation.ml()(_))

  def mlClassification = showHtml(documentation.mlClassification()(_))

  def mlRegression = showHtml(documentation.mlRegression()(_))

  def mlClusterization = showHtml(documentation.mlClusterization()(_))

  def userManagement = showHtml(documentation.userManagement()(_))

  def dataSetImport = showHtml(documentation.dataSetImport()(_))

  def technology = showHtml(documentation.technology()(_))

  private def showHtml(html: WebContext => Html) = Action { implicit request => Ok(html(webContext))}
}