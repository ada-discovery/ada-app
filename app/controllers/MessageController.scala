package controllers

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import akka.stream.scaladsl.Source
import akka.util.ByteString
import be.objectify.deadbolt.scala.DeadboltActions
import models.Message
import models.security.{SecurityRole, UserManager}
import dataaccess.DescSort
import persistence.RepoTypes.MessageRepo
import play.api.libs.Comet
import play.api.libs.EventSource.EventIdExtractor
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, Controller, Results}
import models.Message.MessageFormat
import play.api.libs.EventSource
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import security.AdaAuthConfig
import controllers.routes.javascript.{MessageController => messageJsRoutes}
import play.api.http.{ContentTypes, HttpEntity}
import play.api.libs.streams.Streams

import scala.concurrent.duration._
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

  private  def eventId(jsObject: JsValue) = Some(((jsObject \ "_id").get.as[BSONObjectID]).stringify)
  private implicit val idExtractor = new EventIdExtractor[JsValue](eventId)

  def eventStream = Action { // deadbolt.SubjectPresent()()
    implicit request =>
//      val source = Source.fromPublisher(Streams.enumeratorToPublisher(repo.stream.map(message => ByteString.fromString(Json.stringify(Json.toJson(message)))))) // [JEventSource[JsValue]()
//      Ok.sendEntity(HttpEntity.Streamed(source, None, Some("text/event-stream"))) //.as("text/event-stream")
//      val someDataStream = Source.fromPublisher(Streams.enumeratorToPublisher(repo.stream.map(message => Json.stringify(Json.toJson(message)))))
      val someDataStream = Source.single("")
      Ok.chunked(someDataStream via EventSource.flow).as(ContentTypes.EVENT_STREAM) // as("text/event-stream")
  }

  def liveClock() = Action {
    val df: DateTimeFormatter = DateTimeFormatter.ofPattern("HH mm ss")
    val tickSource = Source.tick(0 millis, 100 millis, "TICK")
    val source: Source[String, _] = tickSource.map { (tick) =>
      df.format(ZonedDateTime.now())
    }
    Ok.chunked(source via EventSource.flow).as(ContentTypes.EVENT_STREAM)
  }
}