package org.ada.server.runnables

import akka.actor.ActorSystem
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.{DataSetImportRepo, RunnableSpecRepo}
import org.ada.server.models.RunnableSpec
import org.ada.server.services.SchedulerImpl
import org.incal.core.util.ReflectionUtil.currentThreadClassLoader
import play.api.inject.Injector
import reactivemongo.bson.BSONObjectID

import scala.concurrent.{ExecutionContext, Future}

protected[services] class RunnableSchedulerImpl @Inject() (
  val system: ActorSystem,
  injector: Injector,
  val repo: RunnableSpecRepo)(
  implicit ec: ExecutionContext
) extends SchedulerImpl[RunnableSpec, BSONObjectID]("runnable") {

  override protected def exec(runnableSpec: RunnableSpec) = {
    val instance = getInjectedInstance(runnableSpec.runnableClassName)

    instance match {
      case runnable: Runnable => runnable.run()

      case _ => ???
    }
  }

  private def getInjectedInstance(className: String) = {
    val clazz = Class.forName(className, true, currentThreadClassLoader)
    injector.instanceOf(clazz)
  }
}