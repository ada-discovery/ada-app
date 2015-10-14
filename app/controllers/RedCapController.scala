package controllers

import javax.inject.Inject

import models.Page
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import services.RedCapService
import views.html
import play.api.mvc.{Action, Controller}

class RedCapController @Inject() (
    redCapService: RedCapService,
    messagesApi: MessagesApi
  ) extends Controller {

  val limit = 20

  def index = Action { Redirect(routes.RedCapController.listFieldNames()) }

  def listRecords(page: Int, orderBy: String, filter: String) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    redCapService.listRecords(page, orderBy, filter).map( items =>
      Ok(html.redcap.listRecords(Page(items.drop(page * limit).take(limit), page, page * limit, items.size), orderBy, filter))
    )
  }

  def listMetadatas(page: Int, orderBy: String, filter: String) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    redCapService.listMetadatas(page, orderBy, filter).map( items =>
      Ok(html.redcap.listMetadatas(Page(items.drop(page * limit).take(limit), page, page * limit, items.size), orderBy, filter))
    )
  }

  def listFieldNames(page: Int, orderBy: String, filter: String) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    redCapService.listFieldNames(page, orderBy, filter).map( items =>
      Ok(html.redcap.listFieldNames(Page(items.drop(page * limit).take(limit), page, page * limit, items.size), orderBy, filter))
    )
  }

  def showRecord(id: String) = Action.async { implicit request =>
    redCapService.getRecord(id).map { foundItems =>
      if (foundItems.isEmpty) {
        NotFound(s"Entity #$id not found")
      } else {
        implicit val msg = messagesApi.preferred(request)
        Ok(html.redcap.showRecord(foundItems.head))
      }
    }
  }

  def showMetadata(id: String) = Action.async { implicit request =>
    redCapService.getMetadata(id).map { foundItems =>
      if (foundItems.isEmpty) {
        NotFound(s"Entity #$id not found")
      } else {
        implicit val msg = messagesApi.preferred(request)
        Ok(html.redcap.showMetadata(foundItems.head))
      }
    }
  }

  def showFieldName(id: String) = Action.async { implicit request =>
    redCapService.getFieldName(id).map { foundItems =>
      if (foundItems.isEmpty) {
        NotFound(s"Entity #$id not found")
      } else {
        implicit val msg = messagesApi.preferred(request)
        Ok(html.redcap.showFieldName(foundItems.head))
      }
    }
  }
}