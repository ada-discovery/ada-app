package runnables.ohdsi

import javax.inject.Inject
import org.ada.server.dataaccess.StreamSpec
import org.ada.server.models.datatrans.{ResultDataSetSpec, SwapFieldsDataSetTransformation}
import org.ada.server.models.{Field, FieldTypeId, StorageType}
import org.ada.server.services.ServiceTypes.DataSetCentralTransformer
import org.incal.core.runnables.InputFutureRunnableExt

class ImportOHDSIConceptDataSet @Inject()(transformer: DataSetCentralTransformer) extends InputFutureRunnableExt[ImportOHDSIConceptDataSetSpec] {

  private val newFields = Seq(
    Field("code", Some("Code"), FieldTypeId.Integer),
    Field("label", Some("Label"), FieldTypeId.String)
  )

  override def runAsFuture(input: ImportOHDSIConceptDataSetSpec) = {
    transformer(
      SwapFieldsDataSetTransformation(
        sourceDataSetId = input.rawOhdsiConceptDataSetId,
        resultDataSetSpec = ResultDataSetSpec(
          id = input.targetOhdsiConceptDataSetId,
          name = "OHDSI Concept",
          storageType = StorageType.ElasticSearch
        ),
        newFields = newFields,
        streamSpec = input.streamSpec
      )
    )
  }
}

case class ImportOHDSIConceptDataSetSpec(
  rawOhdsiConceptDataSetId: String,
  targetOhdsiConceptDataSetId: String,
  streamSpec: StreamSpec
)
