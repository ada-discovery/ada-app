package controllers

import javax.inject.{Inject, Singleton}

import controllers.Assets.Asset
import play.api.Configuration
import play.api.mvc.Result
import play.api.http.Status
import play.api.mvc.Results.{BadRequest, NotFound}
import play.api.mvc.AnyContent
import play.api.mvc.Action

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CustomDirAssets @Inject() (assets: Assets, configuration: Configuration) {

  private val externalAssetPaths = configuration.getStringSeq("assets.external_paths").getOrElse(Nil).toList

  def versioned(
    primaryPath: String,
    file: Asset
  ) = findAssetAux(primaryPath :: externalAssetPaths, Assets.versioned(_, file))

  def at(
    primaryPath: String,
    file: String,
    aggressiveCaching: Boolean = false
  ) = findAssetAux(primaryPath :: externalAssetPaths, Assets.at(_, file, aggressiveCaching))

  private def findAssetAux(
    paths: Seq[String],
    assetAction: String => Action[AnyContent]
  ) = Action.async { implicit request =>
    def isNotFound(result: Result) = result.header.status == Status.NOT_FOUND

    if (paths.isEmpty)
      Future(BadRequest("No paths provided for an asset lookup."))
    else
      paths.foldLeft(
        Future(NotFound: Result)
      )(
        (resultFuture, path) => resultFuture.flatMap( result =>
          if (isNotFound(result)) assetAction(path)(request) else Future(result)
        )
      )
  }
}