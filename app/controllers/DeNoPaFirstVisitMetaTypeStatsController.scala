package controllers

import javax.inject.Inject

import models.{MetaTypeStats, Page}
import persistence.DeNoPaBaselineMetaTypeStatsRepo
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import views.html

class DeNoPaFirstVisitMetaTypeStatsController @Inject() (
    metaTypeStatsRepo: DeNoPaBaselineMetaTypeStatsRepo,
    messagesApi: MessagesApi
  ) extends MetaTypeStatsController(metaTypeStatsRepo, messagesApi) {

  override def showView(item : MetaTypeStats)(implicit msg: Messages, request: RequestHeader) =
    html.denopametatype.show(item).asInstanceOf[Html]

  override def listView(currentPage: Page[MetaTypeStats], currentOrderBy: String, currentFilter: String, currentSearchField : String)(implicit msg: Messages, request: RequestHeader) =
    html.denopametatype.list(currentPage, currentOrderBy, currentFilter, currentSearchField).asInstanceOf[Html]
}