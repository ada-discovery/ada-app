package models.sampleRequest

import play.api.libs.json.{Json, OFormat, OWrites, Reads}

import java.time.ZonedDateTime
import java.util.UUID

case class RequestRepresentation(id: Long,
                                 uuid: UUID,
                                 requester: UserRepresentation,
                                 status: Option[OverviewStatus.Value],
                                 organisations: List[OrganisationRepresentation],
                                 requestDetail: RequestDetailRepresentation,
                                 createdDate: Option[ZonedDateTime],
                                 relatedRequests: Option[Seq[RequestRepresentation]]
                                )

object RequestRepresentation {
  implicit val requestRepresentationRead: Reads[RequestRepresentation] = Json.reads[RequestRepresentation]
  implicit val requestRepresentationWrite: OWrites[RequestRepresentation] = Json.writes[RequestRepresentation]
}
