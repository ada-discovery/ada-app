package controllers

import javax.inject.Inject

import models.{DataSpaceMetaInfo, HtmlSnippet, HtmlSnippetId}
import models.security.UserManager
import org.incal.core.dataaccess.Criterion._
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import security.AdaAuthConfig
import services.DataSpaceService
import org.incal.play.controllers.BaseController
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil._
import persistence.RepoTypes.HtmlSnippetRepo
import views.html.layout
import play.api.cache.Cached

import scala.concurrent.Future

class AppController @Inject() (
    dataSpaceService: DataSpaceService,
    htmlSnippetRepo: HtmlSnippetRepo,
    val userManager: UserManager,
    cached: Cached
  ) extends BaseController with AdaAuthConfig {

  private val logger = Logger

  def index = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Homepage).map( html =>
      Ok(layout.home(html))
    )
  }

  def contact = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Contact).map( html =>
      Ok(layout.contact(html))
    )
  }

  private def getHtmlSnippet(
    id: HtmlSnippetId.Value
  ): Future[Option[String]] =
    htmlSnippetRepo.find(Seq("snippetId" #== id)).map(_.filter(_.active).headOption.map(_.content))

  // TODO: move elsewhere
  def dataSets = restrictSubjectPresentAnyNoCaching(deadbolt) {
    implicit request =>
      for {
        user <- currentUser(request)
        metaInfos <- user match {
          case None => Future(Traversable[DataSpaceMetaInfo]())
          case Some(user) => dataSpaceService.getTreeForUser(user)
        }
      } yield {
        user.map { user =>
          logger.info("Studies accessed by " + user.ldapDn)
          val dataSpacesNum = metaInfos.map(countDataSpacesNumRecursively).sum
          val dataSetsNum = metaInfos.map(countDataSetsNumRecursively).sum
          val userFirstName = user.ldapDn.split("\\.", -1).head.capitalize

          Ok(layout.dataSets(userFirstName, dataSpacesNum, dataSetsNum, metaInfos))
        }.getOrElse(
          BadRequest("No logged user.")
        )
      }
  }

  private def countDataSetsNumRecursively(dataSpace: DataSpaceMetaInfo): Int =
    dataSpace.children.foldLeft(dataSpace.dataSetMetaInfos.size) {
      case (count, dataSpace) => count + countDataSetsNumRecursively(dataSpace)
    }

  private def countDataSpacesNumRecursively(dataSpace: DataSpaceMetaInfo): Int =
    dataSpace.children.foldLeft(1) {
      case (count, dataSpace) => count + countDataSpacesNumRecursively(dataSpace)
    }
}