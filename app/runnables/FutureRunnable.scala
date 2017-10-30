package runnables

import javax.inject.Inject

import models.AdaException
import persistence.dataset.DataSetAccessorFactory

import scala.reflect.runtime.universe.Type
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait FutureRunnable extends Runnable {
  protected val timeout = 10 hours

  def runAsFuture: Future[Unit]

  override def run = Await.result(runAsFuture, timeout)
}

trait InputRunnable[I] {

  def inputType: Type

  def run(input: I): Unit
}

trait InputFutureRunnable[I] extends InputRunnable[I] {
  protected val timeout = 10 hours

  def runAsFuture(input: I): Future[Unit]

  override def run(input: I) = Await.result(runAsFuture(input), timeout)
}

trait DsaInputFutureRunnable[I] extends InputFutureRunnable[I] {

  @Inject var dsaf: DataSetAccessorFactory = _

  protected def dsa(dataSetId: String) = dsaf(dataSetId).getOrElse(
    throw new AdaException(s"Data set id ${dataSetId} not found.")
  )

  protected def dataSetRepo(dataSetId: String) = dsa(dataSetId).dataSetRepo
}