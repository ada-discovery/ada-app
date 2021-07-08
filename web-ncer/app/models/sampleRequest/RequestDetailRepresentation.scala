package models.sampleRequest

import play.api.libs.json.{Json, OFormat, OWrites, Reads}

case class RequestDetailRepresentation(id: Option[Long],
                                       title: Option[String],
                                       background: Option[String],
                                       researchQuestion: Option[String],
                                       hypothesis: Option[String],
                                       methods: Option[String],
                                       relatedRequestNumber: Option[String],
                                       principalInvestigator: PrincipalInvestigatorRepresentation,
                                       searchQuery: Option[String],
                                       requestType: Seq[RequestType.Value],
                                       combinedRequest: Option[Boolean])

object RequestDetailRepresentation {
  implicit val requestDetailRepresentationRead: Reads[RequestDetailRepresentation] = Json.reads[RequestDetailRepresentation]
  implicit val requestDetailRepresentationWrite: OWrites[RequestDetailRepresentation] = Json.writes[RequestDetailRepresentation]
}
