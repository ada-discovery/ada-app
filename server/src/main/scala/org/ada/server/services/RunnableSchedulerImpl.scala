package org.ada.server.services

import akka.actor.ActorSystem
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.BaseRunnableSpecRepo
import org.ada.server.models.{BaseRunnableSpec, RunnableSpec}
import org.ada.server.models.RunnableSpec.BaseRunnableSpecIdentity
import reactivemongo.bson.BSONObjectID

import scala.concurrent.{ExecutionContext, Future}

private[services] class RunnableSchedulerImpl @Inject() (
  val system: ActorSystem,
  val repo: BaseRunnableSpecRepo,
  val inputExec: InputExec[BaseRunnableSpec])(
  implicit ec: ExecutionContext
) extends InputExecSchedulerImpl[BaseRunnableSpec, BSONObjectID]("runnable") {
  override protected def formatId(id: BSONObjectID) = id.stringify
}