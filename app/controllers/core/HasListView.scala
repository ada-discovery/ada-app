package controllers.core

import models.Page
import play.twirl.api.Html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait HasListView[E] {

  protected type ListViewData

  protected def getListViewData(page: Page[E]): Future[ListViewData]

  protected def listView: WebContext => ListViewData => Html

  protected def listViewWithContext(
    data: ListViewData)(
    implicit context: WebContext
  ) =
    listView(context)(data)
}

trait HasBasicListView[E] extends HasListView[E] {

  override protected type ListViewData = Page[E]

  override protected def getListViewData(page: Page[E]) = Future(page)
}
