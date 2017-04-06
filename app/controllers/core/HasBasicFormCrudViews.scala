package controllers.core

import play.api.data.Form

case class IdForm[ID, E](id: ID, form: Form[E])

trait HasBasicFormCrudViews[E, ID]
  extends HasBasicFormCreateView[E]
    with HasBasicFormShowView[E, ID]
    with HasBasicFormEditView[E, ID]
    with HasBasicListView[E]