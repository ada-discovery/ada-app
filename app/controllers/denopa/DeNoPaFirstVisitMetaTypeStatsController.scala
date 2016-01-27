package controllers.denopa

import javax.inject.{Inject, Named}
import controllers.MetaTypeStatsController
import models.{MetaTypeStats, Page}
import persistence.RepoTypeRegistry.MetaTypeStatsRepo
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import views.html
import controllers.denopa.{routes => denoparoutes}

class DeNoPaFirstVisitMetaTypeStatsController @Inject() (
    @Named("DeNoPaFirstVisitMetaTypeStatsRepo") metaTypeStatsRepo: MetaTypeStatsRepo
  ) extends MetaTypeStatsController(metaTypeStatsRepo) {

  override protected def showView(item : MetaTypeStats)(implicit msg: Messages, request: RequestHeader) =
    html.denopametatype.show(
      "first visit field",
      item,
      denoparoutes.DeNoPaFirstVisitMetaTypeStatsController.find()
    )

  override protected def listView(currentPage: Page[MetaTypeStats])(implicit msg: Messages, request: RequestHeader) =
    html.denopametatype.list(
      "first visit field",
      currentPage,
      denoparoutes.DeNoPaFirstVisitMetaTypeStatsController.find,
      denoparoutes.DeNoPaFirstVisitMetaTypeStatsController.find(),
      denoparoutes.DeNoPaFirstVisitMetaTypeStatsController.get
    )
}