package controllers.core

import java.io.File

import controllers.AssetsBuilder
import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Result}
import play.utils.UriEncoding

import scala.concurrent.Future

object ExternalAsset extends App {

  private val dblSlashPattern = """//+""".r
  private val assetBuilder = new AssetsBuilder(DummyErrorHandler)

  /**
    * Get the name of the resource for a static resource. Used by `at`.
    *
    * @param path the root folder for searching the static resource files, such as `"/public"`. Not URL encoded.
    * @param file the file part extracted from the URL. May be URL encoded (note that %2F decodes to literal /).
    */
  private def resourceNameAt(path: String, file: String): Option[String] = {
    val decodedFile = UriEncoding.decodePath(file, "utf-8")
    def dblSlashRemover(input: String): String = dblSlashPattern.replaceAllIn(input, "/")
    val resourceName = dblSlashRemover(s"/$path/$decodedFile")
    val resourceFile = new File(resourceName)
    val pathFile = new File(path)
    if (!resourceFile.getCanonicalPath.startsWith(pathFile.getCanonicalPath)) {
      None
    } else {
      Some(resourceName)
    }
  }

//  println(resourceNameAt("/public","image1.png"))
//
//  println(resourceNameAt("/public","logos/image2.png"))
//
//  println(resourceNameAt("/home/peter/Downloads","logos/image2.png"))

  assetBuilder.at("/public","image1.png")
}

object DummyErrorHandler extends HttpErrorHandler {
  override def onClientError(
    request: RequestHeader,
    statusCode: Int,
    message: String
  ): Future[Result] = ???

  override def onServerError(
    request: RequestHeader,
    exception: Throwable
  ): Future[Result] = ???
}
