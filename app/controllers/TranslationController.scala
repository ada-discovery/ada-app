package controllers

import javax.inject.Inject

import models.{Page, Translation}
import persistence.RepoTypes._
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText}
import play.api.i18n.{Messages}
import play.api.libs.json.{Json}
import play.api.mvc.{Request, RequestHeader}
import reactivemongo.bson.BSONObjectID
import views.html

class TranslationController @Inject() (
    translationRepo: TranslationRepo
  ) extends CrudControllerImpl[Translation, BSONObjectID](translationRepo) with AdminRestrictedCrudController[BSONObjectID] {

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "original" -> nonEmptyText,
      "translated" -> nonEmptyText
    )(Translation.apply)(Translation.unapply))

  override protected val home =
    Redirect(routes.TranslationController.listAll())

  override protected def createView(f : Form[Translation])(implicit msg: Messages, request: Request[_]) =
    html.translation.create(f)

  override protected def showView(id: BSONObjectID, f : Form[Translation])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[Translation])(implicit msg: Messages, request: Request[_]) =
    html.translation.edit(id, f)

  override protected def listView(currentPage: Page[Translation])(implicit msg: Messages, request: Request[_]) =
    html.translation.list(currentPage)

  override protected val defaultCreateEntity =
    new Translation(None, null, null)
}