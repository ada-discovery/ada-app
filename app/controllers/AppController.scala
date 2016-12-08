package controllers

import javax.inject.Inject

import persistence.RepoTypes.DataSpaceMetaInfoRepo
import persistence.dataset.DataSpaceMetaInfoRepo
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller}
import play.api.routing.JavaScriptReverseRouter
import views.html.layout

class AppController @Inject() (dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo) extends Controller {

  def index = Action { implicit request =>
    Ok(layout.home())
  }

  // TODO: move elsewhere
  def studies = Action.async { implicit request =>
    DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo).map( metaInfos =>
      Ok(layout.studies(metaInfos))
    )
  }

  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.AdminController.listRunnables
      )
    ).as("text/javascript")
  }
}