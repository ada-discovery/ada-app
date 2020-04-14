package org.ada.server.services

import java.util.Date

import akka.actor.ActorSystem
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.{MessageRepo, RunnableSpecRepo}
import org.ada.server.models.RunnableSpec
import org.ada.server.services.ServiceTypes.RunnableScheduler
import org.ada.server.util.MessageLogger
import org.incal.core.util.ReflectionUtil.currentThreadClassLoader
import play.api.Logger
import play.api.inject.Injector
import reactivemongo.bson.BSONObjectID

import scala.concurrent.{ExecutionContext, Future}

protected[services] class RunnableSchedulerImpl @Inject() (
  val system: ActorSystem,
  injector: Injector,
  val repo: RunnableSpecRepo,
  messageRepo: MessageRepo)(
  implicit ec: ExecutionContext
) extends SchedulerImpl[RunnableSpec, BSONObjectID]("runnable") {

  private val messageLogger = MessageLogger(logger, messageRepo)

  override protected def exec(runnableSpec: RunnableSpec) = {
    val instance = getInjectedInstance(runnableSpec.runnableClassName)

    instance match {
      case runnable: Runnable => runnable.run()

      case _ => ???
    }

    for {
      _ <- if (runnableSpec._id.isDefined) {
        // update if id exists, i.e., it's a persisted import
        val updatedInput = runnableSpec.copy(timeLastExecuted = Some(new Date()))
        repo.update(updatedInput).map(_ => ())
      } else
        Future(())
    } yield
      messageLogger.info(s"Runnable '${runnableSpec.runnableClassName}' successfully finished.")
  }

  private def getInjectedInstance(className: String) = {
    val clazz = Class.forName(className, true, currentThreadClassLoader)
    injector.instanceOf(clazz)
  }
}