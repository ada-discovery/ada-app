package controllers.denopa

import javax.inject.{Inject, Named}
import controllers.MetaTypeStatsController
import models.{MetaTypeStats, Page}
import persistence.RepoTypeRegistry.MetaTypeStatsRepo
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import views.html

class DeNoPaFirstVisitMetaTypeStatsController @Inject() (
    @Named("DeNoPaFirstVisitMetaTypeStatsRepo") metaTypeStatsRepo: MetaTypeStatsRepo
  ) extends MetaTypeStatsController(metaTypeStatsRepo) {

  override protected def showView(
    item : MetaTypeStats)(
    implicit msg: Messages, request: RequestHeader
  ) =
    html.denopametatype.showFirstVisit(item)

  override protected def listView(
    currentPage: Page[MetaTypeStats],
    currentOrderBy: String,
    currentFilter: String,
    currentSearchField : String)(
    implicit msg: Messages, request: RequestHeader
  ) =
    html.denopametatype.listFirstVisit(currentPage, currentOrderBy, currentFilter, currentSearchField)
}