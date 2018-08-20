package controllers.core

import be.objectify.deadbolt.scala.SubjectActionBuilder
import play.api.mvc.{Action, AnyContent}
import play.api.mvc.BodyParsers.parse
import util.SecurityUtil.AuthenticatedAction

object AuthAction {

  def apply(
    action: AuthenticatedAction[AnyContent]
  ): Action[AnyContent] =
    SubjectActionBuilder(None).async(parse.anyContent) { authRequest =>
      action(authRequest)
    }
}