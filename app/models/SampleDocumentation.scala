package models

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import reactivemongo.play.json.JSONSerializationPack._

case class SampleDocumentation(
    _id: Option[BSONObjectID] = None,
    dataSetId: String,
    timeUpdated: Date = new Date()
)


object SampleDocumentation {
    implicit val sampleDocumentationFormat = Json.format[SampleDocumentation]

    implicit object SampleDocumentationIdentity extends BSONObjectIdentity[SampleDocumentation] {
        def of(entity: SampleDocumentation): Option[BSONObjectID] = entity._id

        protected def set(entity: SampleDocumentation, id: Option[BSONObjectID]) = entity.copy(_id = id)
    }

}