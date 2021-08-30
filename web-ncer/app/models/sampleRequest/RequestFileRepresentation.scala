package models.sampleRequest

import play.api.libs.json.{Json, OWrites, Reads}

import java.time.ZonedDateTime
import java.util.UUID

case class RequestFileRepresentation(uuid: UUID,
                                     owner: UserRepresentation,
                                     request: RequestRepresentation,
                                     createdDate: ZonedDateTime,
                                     lastModifiedDate: ZonedDateTime,
                                     fileByteSize: Long,
                                     requestFileType: RequestFileType.Value,
                                     fileName: String,
                                     uploader: UserRepresentation
                                    )

object RequestFileRepresentation {
  implicit val requestFileRepresentationRead: Reads[RequestFileRepresentation] = Json.reads[RequestFileRepresentation]
  implicit val requestFileRepresentationWrite: OWrites[RequestFileRepresentation] = Json.writes[RequestFileRepresentation]
}
