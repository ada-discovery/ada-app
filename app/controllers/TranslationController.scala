package controllers

import javax.inject.Inject

import models.{Page, Translation}
import persistence.RepoTypeRegistry._
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText}
import play.api.i18n.{Messages}
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{Json}
import play.api.mvc.{RequestHeader}
import reactivemongo.bson.BSONObjectID
import views.html

class TranslationController @Inject() (
    translationRepo: TranslationRepo
  ) extends CrudController[Translation, BSONObjectID](translationRepo) {

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option(BSONObjectID.generate: BSONObjectID)),
      "original" -> nonEmptyText,
      "translated" -> nonEmptyText
    )(Translation.apply)(Translation.unapply))

  override protected val home =
    Redirect(routes.TranslationController.listAll())

  override protected def createView(f : Form[Translation])(implicit msg: Messages, request: RequestHeader) =
    html.translation.create(f)

  override protected def showView(id: BSONObjectID, f : Form[Translation])(implicit msg: Messages, request: RequestHeader) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[Translation])(implicit msg: Messages, request: RequestHeader) =
    html.translation.edit(id, f)

  override protected def listView(currentPage: Page[Translation], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.translation.list(currentPage, currentOrderBy, currentFilter)

  override protected def toJsonCriteria(string : String) =
    if (!string.isEmpty)
      Some(Json.obj("original" -> Json.obj("$regex" -> (string + ".*"), "$options" -> "i")))
    else
      None

  override protected val defaultCreateEntity =
    new Translation(None, null, null)
}