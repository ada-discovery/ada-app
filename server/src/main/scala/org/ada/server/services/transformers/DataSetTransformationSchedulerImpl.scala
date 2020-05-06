package org.ada.server.services.transformers

import akka.actor.ActorSystem
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.DataSetTransformationRepo
import org.ada.server.models.datatrans.DataSetTransformation.DataSetMetaTransformationIdentity
import org.ada.server.models.datatrans.DataSetMetaTransformation
import org.ada.server.services.{InputExec, InputExecSchedulerImpl, LookupCentralExec}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext

protected[services] class DataSetTransformationSchedulerImpl @Inject() (
  val system: ActorSystem,
  val repo: DataSetTransformationRepo,
  val inputExec: InputExec[DataSetMetaTransformation])(
  implicit ec: ExecutionContext
) extends InputExecSchedulerImpl[DataSetMetaTransformation, BSONObjectID]("data set transformation") {
  override protected def formatId(id: BSONObjectID) = id.stringify
}