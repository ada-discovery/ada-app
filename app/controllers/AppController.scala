package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import controllers.core.WebContext
import models.DataSpaceMetaInfo
import models.security.UserManager
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, AnyContent, Controller, Request}
import play.api.routing.JavaScriptReverseRouter
import security.AdaAuthConfig
import services.DataSpaceService
import util.SecurityUtil._
import views.html.layout

import scala.concurrent.Future
import scala.io.Source

class AppController @Inject() (
    dataSpaceService: DataSpaceService,
    val userManager: UserManager,
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets
  ) extends Controller with AdaAuthConfig {

  @Inject var deadbolt: DeadboltActions = _

  private val correlationFileName = "/home/peter/Data/mpower_ldopa_challenge_correlations/dyskinesia-corrs.csv"

  private implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets)
  }

  def index = Action { implicit request =>
    Ok(layout.home())
  }

  // TODO: move elsewhere
  def studies = restrictSubjectPresentAny(deadbolt) {
    implicit request =>
      for {
        user <- currentUser(request)
        metaInfos <- user match {
          case None => Future(Traversable[DataSpaceMetaInfo]())
          case Some(user) => dataSpaceService.getTreeForUser(user)
        }
      } yield {
        user.map { user =>
          val dataSpacesNum = metaInfos.map(countDataSpacesNumRecursively).sum
          val dataSetsNum = metaInfos.map(countDataSetsNumRecursively).sum
          val userFirstName = user.ldapDn.split("\\.", -1).head.capitalize

          Ok(layout.studies(userFirstName, dataSpacesNum, dataSetsNum, metaInfos))
        }.getOrElse(
          BadRequest("No logged user.")
        )
      }
  }

  private def countDataSetsNumRecursively(dataSpace: DataSpaceMetaInfo): Int =
    dataSpace.children.foldLeft(dataSpace.dataSetMetaInfos.size) {
      case (count, dataSpace) => count + countDataSetsNumRecursively(dataSpace)
    }

  private def countDataSpacesNumRecursively(dataSpace: DataSpaceMetaInfo): Int =
    dataSpace.children.foldLeft(1) {
      case (count, dataSpace) => count + countDataSpacesNumRecursively(dataSpace)
    }

  def contact = Action { implicit request =>
    Ok(layout.contact())
  }

  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.AdminController.listRunnables
      )
    ).as("text/javascript")
  }
}