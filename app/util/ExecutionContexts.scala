package util

import java.util.concurrent.Executors

import play.api.libs.concurrent.Akka
import play.api.Play.current

import scala.concurrent.ExecutionContext

object ExecutionContexts {

  def newInstance(threadNum: Int): ExecutionContext = new ExecutionContext {
    val threadPool = Executors.newFixedThreadPool(threadNum)
    override def reportFailure(cause: Throwable) = {}
    override def execute(runnable: Runnable): Unit = threadPool.submit(runnable)
    def shutdown() = threadPool.shutdown()
  }

  val fixed1000ThreadEC: ExecutionContext = newInstance(1000)

  //  val SynapseExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("synapse.api.exec-context")
}
