package controllers

import javax.inject.{Named, Inject}

import models.{MetaTypeStats, Page}
import persistence.MetaTypeStatsRepo
import persistence.RepoTypeRegistry.MetaTypeStatsRepo2
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import views.html

class DeNoPaFirstVisitMetaTypeStatsController @Inject() (
    @Named("DeNoPaFirstVisitMetaTypeStatsRepo") metaTypeStatsRepo: MetaTypeStatsRepo,
    messagesApi: MessagesApi
  ) extends MetaTypeStatsController(metaTypeStatsRepo, messagesApi) {

  override def showView(item : MetaTypeStats)(implicit msg: Messages, request: RequestHeader) =
    html.denopametatype.showFirstVisit(item).asInstanceOf[Html]

  override def listView(currentPage: Page[MetaTypeStats], currentOrderBy: String, currentFilter: String, currentSearchField : String)(implicit msg: Messages, request: RequestHeader) =
    html.denopametatype.listFirstVisit(currentPage, currentOrderBy, currentFilter, currentSearchField).asInstanceOf[Html]
}