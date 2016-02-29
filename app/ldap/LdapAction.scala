package ldap

import javax.inject.{Inject, Singleton}

import be.objectify.deadbolt.scala._
import play.api.mvc._

import security.CustomDeadboltHandler
import be.objectify.deadbolt.scala.cache.HandlerCache

/**
  * TODO: proper implementation needed.
  * TODO: try using CustomDeadboltHandler.beforeAuthCheck() instead
  * Extension of deadbolt action, specifically for use with ldap.
  * Wraps around deadbolt action such that calls like restrict, subjectpresent, etc are still callable
  *
  */
@Singleton
class LdapActions@Inject()(analyzer: ScalaAnalyzer, handlers: HandlerCache, ecProvider: ExecutionContextProvider) extends DeadboltActions(analyzer, handlers, ecProvider){
  def apply[T](restrictRoles: Seq[String], bodyParser: BodyParser[T])(code: Request[T] => Result) = Restrict(restrictRoles.toArray, new CustomDeadboltHandler) {
    Action(bodyParser) { implicit request =>
      code(request)
    }
  }
}

