package controllers.core

import play.api.http.HeaderNames
import play.api.mvc._
import util.noCacheSetting
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class NoCacheAction extends ActionBuilder[Request] {

  def invokeBlock[A](
    request: Request[A],
    block: (Request[A]) => Future[Result]
  ) =
    block(request).map(_.withHeaders(HeaderNames.CACHE_CONTROL -> noCacheSetting))
}

case class WithNoCaching[A](action: Action[A]) extends Action[A] {

  def apply(request: Request[A]): Future[Result] = {
    action(request).map(_.withHeaders(HeaderNames.CACHE_CONTROL -> noCacheSetting))
  }

  lazy val parser = action.parser
}