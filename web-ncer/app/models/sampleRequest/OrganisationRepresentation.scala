package models.sampleRequest



import play.api.libs.json.{Json, OFormat, Reads}

import java.util.UUID

case class OrganisationRepresentation(id: Long,
                                      name: String,
                                      shortName: String,
                                      activated: Boolean,
                                      uuid: UUID,
                                      requestTypes: Seq[RequestType.Value]
                                      )

object OrganisationRepresentation {
  implicit val organisationRepresentationFormat: OFormat[OrganisationRepresentation] = Json.format[OrganisationRepresentation]
}

