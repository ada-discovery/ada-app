package controllers

import javax.inject.Inject

import controllers.core._
import models.{Translation}
import persistence.RepoTypes._
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText}
import reactivemongo.bson.BSONObjectID
import views.html.{translation => view}

class TranslationController @Inject() (
    translationRepo: TranslationRepo
  ) extends CrudControllerImpl[Translation, BSONObjectID](translationRepo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasBasicFormCrudViews[Translation, BSONObjectID] {

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "original" -> nonEmptyText,
      "translated" -> nonEmptyText
    )(Translation.apply)(Translation.unapply))

  override protected val home = Redirect(routes.TranslationController.find())

  override protected[controllers] def createView = { implicit ctx => view.create(_) }
  override protected[controllers] def showView = editView
  override protected[controllers] def editView = { implicit ctx => view.edit(_) }
  override protected[controllers] def listView = { implicit ctx => view.list(_) }
}