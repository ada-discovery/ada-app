package models.sampleRequest

import play.api.libs.json.{Json, OFormat, OWrites, Reads}

case class PrincipalInvestigatorRepresentation(id: Option[Long],
                                               name: Option[String],
                                               email: Option[String],
                                               jobTitle: Option[String],
                                               affiliation: Option[String])


object PrincipalInvestigatorRepresentation {
  implicit val principalInvestigatorRepresentationReads: Reads[PrincipalInvestigatorRepresentation] = Json.reads[PrincipalInvestigatorRepresentation]
  implicit val principalInvestigatorRepresentationWrite: OWrites[PrincipalInvestigatorRepresentation] = Json.writes[PrincipalInvestigatorRepresentation]
}
