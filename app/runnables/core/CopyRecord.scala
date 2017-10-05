package runnables.core

import models.AdaException
import models.DataSetFormattersAndIds.JsObjectIdentity
import reactivemongo.bson.BSONObjectID
import runnables.DsaInputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CopyRecord extends DsaInputFutureRunnable[RecordSpec] {

  private val idName = JsObjectIdentity.name

  override def runAsFuture(spec: RecordSpec) = {
    val repo = dataSetRepo(spec.dataSetId)

    for {
      // get a requested record
      recordOption <- repo.get(spec.recordId)

      // clean id and save a copy
      _ <- recordOption.map( record => repo.save(record.-(idName))).getOrElse(
        throw new AdaException(s"Record ${spec.recordId} not found.")
      )
    } yield
      ()
  }

  override def inputType = typeOf[RecordSpec]
}

case class RecordSpec(dataSetId: String, recordId: BSONObjectID)