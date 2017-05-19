package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import models.Message
import models.security.{SecurityRole, UserManager}
import dataaccess.DescSort
import persistence.RepoTypes.MessageRepo
import play.api.libs.Comet
import play.api.libs.EventSource.EventIdExtractor
import play.api.libs.json.{JsValue, JsObject, Json}
import play.api.mvc.{Action, Results, Controller}
import models.Message.MessageFormat
import play.api.libs.EventSource
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import security.AdaAuthConfig
import controllers.routes.javascript.{MessageController => messageJsRoutes}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

class MessageController @Inject() (
    val userManager: UserManager,
    repo: MessageRepo,
    deadbolt: DeadboltActions
  ) extends Controller with AdaAuthConfig {

//  private val SCRIPT_REGEX = """<script>(.*)</script>"""
  private val SCRIPT_REGEX = """<script\b[^<]*(?:(?!<\/script\s*>)<[^<]*)*<\/script\s*>"""

  def saveUserMessage(message: String) = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      currentUser(request).flatMap(_.fold(
        Future(BadRequest("No logged user found"))
      ) { user =>
        val escapedMessage = removeScriptTags(message)
        repo.save(Message(None, escapedMessage, Some(user.ldapDn), user.roles.contains(SecurityRole.admin))).map(_=>
          Ok("Done")
        )
      })
    }
  }

  private def removeScriptTags(text: String): String = {
    var result = text
    var regexApplied = false
    do {
      val newResult = result.replaceAll(SCRIPT_REGEX, "")
      regexApplied = !result.equals(newResult)
      result = newResult
    } while (regexApplied)
    result
  }

  def listMostRecent(limit: Int) = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      for {
        messages <- repo.find(sort = Seq(DescSort("_id")), limit = Some(limit))  // ome(0)
      } yield
        Ok(Json.toJson(messages))
    }
  }

  @Deprecated
  def stream = deadbolt.SubjectPresent() {
    Action { implicit request =>
      Ok.stream(repo.stream.map(message => Json.toJson(message)) &> Comet(callback = "parent.newMessage"))
    }
  }

  private  def eventId(jsObject: JsValue) = Some(((jsObject \ "_id").get.as[BSONObjectID]).stringify)
  private implicit val idExtractor = new EventIdExtractor[JsValue](eventId)

  def eventStream = deadbolt.SubjectPresent() {
    Action { implicit request =>
      Ok.chunked(repo.stream.map(message => Json.toJson(message)) &> EventSource[JsValue]()) //.as("text/event-stream")
    }
  }
}