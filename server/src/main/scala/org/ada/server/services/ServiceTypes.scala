package org.ada.server.services

import org.ada.server.models.{BaseRunnableSpec, RunnableSpec}
import org.ada.server.models.dataimport.DataSetImport
import org.ada.server.models.datatrans.DataSetMetaTransformation
import reactivemongo.bson.BSONObjectID

object ServiceTypes {
  type DataSetCentralImporter = InputExec[DataSetImport]
  type DataSetImportScheduler = Scheduler[DataSetImport, BSONObjectID]

  type DataSetCentralTransformer = InputExec[DataSetMetaTransformation]
  type DataSetTransformationScheduler = Scheduler[DataSetMetaTransformation, BSONObjectID]

  type RunnableExec = InputExec[BaseRunnableSpec]
  type RunnableScheduler = Scheduler[BaseRunnableSpec, BSONObjectID]
}
