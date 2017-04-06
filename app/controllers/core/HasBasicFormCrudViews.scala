package controllers.core

import play.api.data.Form

trait HasBasicFormCrudViews[E, ID]
  extends HasBasicFormCreateView[E]
    with HasBasicFormShowView[E, ID]
    with HasBasicFormEditView[E, ID]
    with HasBasicListView[E]

case class IdForm[ID, E](id: ID, form: Form[E])