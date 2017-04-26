package controllers.core

import models.AdaException
import play.api.data.Form
import play.api.mvc.{AnyContent, Request}

abstract class CreateEditFormViews[T: Manifest, ID] extends HasBasicFormCreateView[T] with HasBasicFormEditView[T, ID] {
  val man = manifest[T]
}

trait HasCreateEditSubTypeFormViews[T, ID] extends HasBasicFormCreateView[T] with HasBasicFormEditView[T, ID] {

  self: CrudControllerImpl[T, ID] =>

  private val concreteClassFieldName = "concreteClass"

  protected val createEditFormViews: Traversable[CreateEditFormViews[_ <: T, ID]]

  protected lazy val classNameFormViewsMap: Map[String, CreateEditFormViews[_ <: T, ID]] =
    createEditFormViews.map ( createEditFormView =>
      (createEditFormView.man.runtimeClass.getName, createEditFormView)
    ).toMap

  override protected def fillForm(entity: T): Form[T] = {
    val concreteClassName = entity.getClass.getName
    getForm(concreteClassName).asInstanceOf[Form[T]].fill(entity)
  }

  override protected def formFromRequest(implicit request: Request[AnyContent]): Form[T] = {
    val concreteClassName =  util.getRequestParamValue(concreteClassFieldName)
    getForm(concreteClassName).bindFromRequest.asInstanceOf[Form[T]]
  }

  protected[controllers] def createView = { implicit ctx =>
    data =>
      val subCreateView = getViews(data)._1
      subCreateView(ctx)(data)
  }

  protected[controllers] def editView = { implicit ctx =>
    data =>
      val subEditView = getViews(data.form)._2
      subEditView(ctx)(data)
  }

  protected def getFormWithViews[E <: T](concreteClassName: String) =
    classNameFormViewsMap.get(concreteClassName).getOrElse(
      throw new AdaException(s"Form and views a sub type '$concreteClassName' not found."))

  protected def getForm(concreteClassName: String) =
    getFormWithViews(concreteClassName).form

  protected def getViews(form: Form[T]): (CreateView, EditView) = {
    val concreteClassName = form.value.map(_.getClass.getName).getOrElse(form(concreteClassFieldName).value.get)
    val formWithViews = classNameFormViewsMap.get(concreteClassName).getOrElse(
      throw new AdaException(s"Form and views for a sub type '$concreteClassName' not found."))
    (formWithViews.createView.asInstanceOf[CreateView], formWithViews.editView.asInstanceOf[EditView])
  }
}