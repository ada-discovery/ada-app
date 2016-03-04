package controllers

import javax.inject.Inject

import persistence.RepoTypes.DataSetMetaInfoRepo
import views.html.layout
import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class AppController @Inject() (dataSetMetaInfoRepo: DataSetMetaInfoRepo) extends Controller {

  def index = Action { implicit request =>
    Ok(layout.home())
  }

  // TODO: move elsewhere
  def studies = Action.async { implicit request =>
    dataSetMetaInfoRepo.find().map( metaInfos =>
      Ok(layout.studies(metaInfos))
    )
  }
}