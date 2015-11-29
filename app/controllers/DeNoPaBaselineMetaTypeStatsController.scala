package controllers

import javax.inject.{Named, Inject}

import models.{MetaTypeStats, Page}
import persistence.RepoTypeRegistry.MetaTypeStatsRepo
import persistence.{AsyncCrudRepo}
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import views.html

class DeNoPaBaselineMetaTypeStatsController @Inject() (
    @Named("DeNoPaBaselineMetaTypeStatsRepo") metaTypeStatsRepo: MetaTypeStatsRepo,
    messagesApi: MessagesApi
  ) extends MetaTypeStatsController(metaTypeStatsRepo, messagesApi) {

  override def showView(item : MetaTypeStats)(implicit msg: Messages, request: RequestHeader) =
    html.denopametatype.showBaseline(item).asInstanceOf[Html]

  override def listView(currentPage: Page[MetaTypeStats], currentOrderBy: String, currentFilter: String, currentSearchField : String)(implicit msg: Messages, request: RequestHeader) =
    html.denopametatype.listBaseline(currentPage, currentOrderBy, currentFilter, currentSearchField).asInstanceOf[Html]
}