package controllers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import javax.inject.Inject
import akka.stream.scaladsl.Source
import be.objectify.deadbolt.scala.DeadboltActions
import org.ada.server.models.Message
import org.ada.server.models.Message._
import org.ada.server.dataaccess.RepoTypes.MessageRepo
import play.api.libs.EventSource.EventIdExtractor
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, Controller, Results}
import org.ada.server.models.Message.MessageFormat
import play.api.libs.EventSource
import reactivemongo.bson.BSONObjectID
import security.AdaAuthConfig
import controllers.routes.javascript.{MessageController => messageJsRoutes}
import org.incal.core.dataaccess.DescSort
import org.incal.play.security.SecurityRole
import play.api.http.{ContentTypes, HttpEntity}
import play.api.libs.streams.Streams

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import services.UserManager

class MessageController @Inject() (
    val userManager: UserManager,
    val repo: MessageRepo,
    deadbolt: DeadboltActions
  ) extends Controller with AdaAuthConfig {

//  private val SCRIPT_REGEX = """<script>(.*)</script>"""
  private val SCRIPT_REGEX = """<script\b[^<]*(?:(?!<\/script\s*>)<[^<]*)*<\/script\s*>"""

  def saveUserMessage(message: String) = deadbolt.SubjectPresent()() {
    implicit request =>
      currentUser(request).flatMap(_.fold(
        Future(BadRequest("No logged user found"))
      ) { user =>
        val escapedMessage = removeScriptTags(message)
        repo.save(Message(None, escapedMessage, Some(user.ldapDn), user.roles.contains(SecurityRole.admin))).map(_=>
          Ok("Done")
        )
      })
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

  def listMostRecent(limit: Int) = deadbolt.SubjectPresent()() {
    implicit request =>
      for {
        messages <- repo.find(sort = Seq(DescSort("_id")), limit = Some(limit))  // ome(0)
      } yield
        Ok(Json.toJson(messages))
  }

//  @Deprecated
//  def stream = deadbolt.SubjectPresent() {
//    Action { implicit request =>
//      Ok.stream(repo.stream.map(message => Json.toJson(message)) &> Comet(callback = "parent.newMessage"))
//    }
//  }

  private def eventId(jsObject: JsValue) = Some(((jsObject \ "_id").get.as[BSONObjectID]).stringify)
  private implicit val idExtractor = new EventIdExtractor[JsValue](eventId)

  def eventStream = Action { // deadbolt.SubjectPresent()()
    implicit request =>
      val requestStart = new java.util.Date()
      val messageStream = repo.stream.filter(_.timeCreated.after(requestStart)).map(message => Json.toJson(message))
      Ok.chunked(messageStream via EventSource.flow).as(ContentTypes.EVENT_STREAM) // as("text/event-stream")
  }

  def liveClock = Action {
    val df: DateTimeFormatter = DateTimeFormatter.ofPattern("HH mm ss")
    val tickSource = Source.tick(0 millis, 100 millis, "TICK")
    val source: Source[String, _] = tickSource.map { (tick) =>
      df.format(ZonedDateTime.now())
    }
    Ok.chunked(source via EventSource.flow).as(ContentTypes.EVENT_STREAM)
  }
}