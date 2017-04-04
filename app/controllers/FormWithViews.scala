package controllers

import controllers.ViewTypes.{EditView, CreateView}
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{Request, Flash}
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID

object ViewTypes {
  type CreateView[E] = (Form[E], WebContext) => Html
  type EditView[E] = (BSONObjectID, Form[E], WebContext) => Html
}

case class FormWithViews[E: Manifest](
  form: Form[E],
  createView: CreateView[E],
  editView: EditView[E]
){
  val man = manifest[E]
}

object FormWithViews {
  def toMap[T](formsWithViews: Traversable[FormWithViews[_ <: T]]): Map[String, (Form[T], CreateView[T], EditView[T])] =
    formsWithViews.map{ formWithViews =>
      (formWithViews.man.runtimeClass.getName, (
        formWithViews.form.asInstanceOf[Form[T]],
        formWithViews.createView.asInstanceOf[CreateView[T]],
        formWithViews.editView.asInstanceOf[EditView[T]]
        ))
    }.toMap
}