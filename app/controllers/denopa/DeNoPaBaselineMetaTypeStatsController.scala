package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.MetaTypeStatsController
import models.{MetaTypeStats, Page}
import persistence.RepoTypeRegistry.MetaTypeStatsRepo
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import views.html

class DeNoPaBaselineMetaTypeStatsController @Inject() (
    @Named("DeNoPaBaselineMetaTypeStatsRepo") metaTypeStatsRepo: MetaTypeStatsRepo
  ) extends MetaTypeStatsController(metaTypeStatsRepo) {

  override protected def showView(
    item : MetaTypeStats)(
    implicit msg: Messages, request: RequestHeader
  ) =
    html.denopametatype.showBaseline(item)

  override protected def listView(
    currentPage: Page[MetaTypeStats],
    currentSearchField : String)(
    implicit msg: Messages, request: RequestHeader
  ) =
    html.denopametatype.listBaseline(currentPage, currentSearchField)
}