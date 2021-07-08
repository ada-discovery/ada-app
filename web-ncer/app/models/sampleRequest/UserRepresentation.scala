package models.sampleRequest

import play.api.libs.json.{Json, OFormat, OWrites, Reads}

import java.util.UUID

case class UserRepresentation(login: Option[String],
                              uuid: Option[UUID],
                              firstName: Option[String],
                              lastName: Option[String],
                              email: Option[String])

object UserRepresentation {
  implicit val userRepresentationRead: Reads[UserRepresentation] = Json.reads[UserRepresentation]
  implicit val userRepresentationWrite: OWrites[UserRepresentation] = Json.writes[UserRepresentation]
}