package models.sampleRequest



import play.api.libs.json.{Json, OFormat, OWrites, Reads}

import java.util.UUID

case class OrganisationRepresentation(id: Option[Long],
                                      name: Option[String],
                                      shortName: Option[String],
                                      activated: Option[Boolean],
                                      uuid: Option[UUID],
                                      requestTypes: Option[Seq[RequestType.Value]]
                                      )

object OrganisationRepresentation {
  implicit val organisationRepresentationRead: Reads[OrganisationRepresentation] = Json.reads[OrganisationRepresentation]
  implicit val organisationRepresentationWrite: OWrites[OrganisationRepresentation] = Json.writes[OrganisationRepresentation]
}

