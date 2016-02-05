package controllers

import javax.inject.Inject

import models.{FieldType, Field, Page}
import models.Dictionary._
import persistence.DictionaryFieldRepo
import persistence.RepoTypeRegistry._
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping, nonEmptyText}
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import reactivemongo.bson.BSONObjectID
import views.html.dictionary

abstract class DictionaryFieldController (
    dictionaryRepo: DictionaryFieldRepo
  ) extends CrudController[Field, String](dictionaryRepo) {

//  override protected val form = Form(
//    mapping(
//      "id" -> ignored(Option(BSONObjectID.generate: BSONObjectID)),
//      "original" -> nonEmptyText,
//      "translated" -> nonEmptyText
//    )(Field.apply)(Field.unapply))

  def router : DictionaryRouter

  override protected val home =
    Redirect(routes.TranslationController.listAll())

  override protected def createView(f : Form[Field])(implicit msg: Messages, request: RequestHeader) =
    dictionary.create(f)

  override protected def showView(name: String, f : Form[Field])(implicit msg: Messages, request: RequestHeader) =
    editView(name, f)

  override protected def editView(name: String, f : Form[Field])(implicit msg: Messages, request: RequestHeader) =
    dictionary.edit(name, f)

  override protected def listView(page: Page[Field])(implicit msg: Messages, request: RequestHeader) =
    dictionary.list(
      " Field",
      page,
      Seq(),
      router.plainFindCall,
      router.findCall,
      router.getCall
    )

  override protected val defaultCreateEntity =
    new Field("", FieldType.Null)
}