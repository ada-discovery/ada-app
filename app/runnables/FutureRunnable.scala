package runnables

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait FutureRunnable extends Runnable {
  val timeout = 5 hours

  def runAsFuture: Future[Unit]

  override def run = Await.result(runAsFuture, timeout)
}
