package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import persistence.dataset.DataSpaceMetaInfoRepo
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller}
import play.api.routing.JavaScriptReverseRouter
import util.SecurityUtil._
import views.html.layout

class AppController @Inject() (dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo) extends Controller {

  @Inject var deadbolt: DeadboltActions = _

  def index = Action { implicit request =>
    Ok(layout.home())
  }

  // TODO: move elsewhere
  def studies = restrictSubjectPresent(deadbolt) {
    Action.async { implicit request =>
      DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo).map(metaInfos =>
        Ok(layout.studies(metaInfos))
      )
    }
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