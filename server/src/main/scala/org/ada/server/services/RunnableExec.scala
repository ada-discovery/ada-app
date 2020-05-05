package org.ada.server.services

import java.util.Date

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{BaseRunnableSpecRepo, MessageRepo, RunnableSpecRepo}
import org.ada.server.models.{BaseRunnableSpec, InputRunnableSpec, RunnableSpec}
import org.ada.server.util.MessageLogger
import org.incal.core.runnables.{FutureRunnable, InputFutureRunnable, InputRunnable}
import org.incal.core.util.ReflectionUtil.currentThreadClassLoader
import play.api.Logger
import play.api.inject.Injector

import scala.concurrent.{ExecutionContext, Future}

private[services] class RunnableExecImpl @Inject() (
  injector: Injector,
  repo: BaseRunnableSpecRepo,
  messageRepo: MessageRepo)(
  implicit ec: ExecutionContext
) extends InputExec[BaseRunnableSpec] {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  override def apply(runnableSpec: BaseRunnableSpec): Future[Unit] = {
    val start = new Date()
    val instance = getInjectedInstance(runnableSpec.runnableClassName)

    val runnableFuture = instance match {
      case runnable: FutureRunnable =>
        runnable.runAsFuture

      case runnable: Runnable =>
        Future(runnable.run())

      case inputRunnable: InputFutureRunnable[_] =>
        runnableSpec match {
          case inputRunnableSpec: InputRunnableSpec[_] =>
            inputRunnable.asInstanceOf[InputFutureRunnable[Any]].runAsFuture(inputRunnableSpec.input)

          case _ =>
            throw new AdaException(s"Input future runnable '${runnableSpec.runnableClassName}' does not have any input.")
        }

      case inputRunnable: InputRunnable[_] =>
        runnableSpec match {
          case inputRunnableSpec: InputRunnableSpec[_] =>
            Future(
              inputRunnable.asInstanceOf[InputRunnable[Any]].run(inputRunnableSpec.input)
            )

          case _ =>
            throw new AdaException(s"Input runnable '${runnableSpec.runnableClassName}' does not have any input.")
        }
    }

    for {
      // execute a runnable
      _ <- runnableFuture

      // log the message
      _ = {
          val execTimeSec = (new Date().getTime - start.getTime) / 1000
          messageLogger.info(s"Runnable '${runnableSpec.runnableClassName}' was successfully executed in ${execTimeSec} sec.")
      }

      // update the time executed
      _ <- {
        // update if id exists, i.e., it's a persisted runnable spec
        val updatedSpec = runnableSpec match {
          case x:RunnableSpec => x.copy(timeLastExecuted = Some(new Date()))
          case x:InputRunnableSpec[_] => x.copy(timeLastExecuted = Some(new Date()))
        }
        repo.update(updatedSpec)
      }
    } yield
      ()
  }

  private def getInjectedInstance(className: String) = {
    val clazz = Class.forName(className, true, currentThreadClassLoader)
    injector.instanceOf(clazz)
  }
}
