package org.ada.server.services.transformers

import java.util.Date

import org.incal.core.dataaccess.StreamSpec
import org.ada.server.json.HasFormat
import org.ada.server.models.ScheduledTime
import org.ada.server.models.datatrans.DataSetTransformation._
import org.ada.server.models.datatrans.{DataSetTransformation, LinkMultiDataSetsTransformation, LinkedDataSetSpec, ResultDataSetSpec}
import play.api.libs.json.Json
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.bson.BSONObjectID

case class SingleFieldLinkMultiDataSetsTransformation(
  _id: Option[BSONObjectID] = None,

  linkedDataSetSpecs: Seq[SingleFieldLinkedDataSetSpec],
  addDataSetIdToRightFieldNames: Boolean,

  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends DataSetTransformation {

  override val sourceDataSetIds = linkedDataSetSpecs.map(_.dataSetId)

  override def copyCore(
    __id: Option[BSONObjectID],
    _timeCreated: Date,
    _timeLastExecuted: Option[Date],
    _scheduled: Boolean,
    _scheduledTime: Option[ScheduledTime]
  ) = copy(
    _id = __id,
    timeCreated = _timeCreated,
    timeLastExecuted = _timeLastExecuted,
    scheduled = _scheduled,
    scheduledTime = _scheduledTime
  )
}

case class SingleFieldLinkedDataSetSpec(
  dataSetId: String,
  linkFieldName: String,
  explicitFieldNamesToKeep: Traversable[String] = Nil
)

object SingleFieldLinkMultiDataSetsTransformation extends HasFormat[SingleFieldLinkMultiDataSetsTransformation] {
  implicit val linkedDataSetSpecFormat = Json.format[SingleFieldLinkedDataSetSpec]
  val format = Json.format[SingleFieldLinkMultiDataSetsTransformation]
}