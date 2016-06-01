package runnables

import javax.inject.Inject

import services.SynapseServiceFactory
import scala.concurrent.Await.result
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Configuration
import scala.concurrent.duration._

class TestSynapseService @Inject() (
    configuration: Configuration,
    synapseServiceFactory: SynapseServiceFactory
  ) extends Runnable {

  private val username = configuration.getString("synapse.api.username").get
  private val password = configuration.getString("synapse.api.password").get
  private val tableId1 = "syn6126231"
  private val tableId2 = "syn6126230"
  private val timeout = 120000 millis

  override def run = {
    val synapseService = synapseServiceFactory(username, password)

    val future = for {
      jobToken <- synapseService.runCsvTableQuery(tableId1, s"SELECT * FROM $tableId1")
      result <- synapseService.getCsvTableResultWait(tableId1, jobToken)
      fileHandle <- synapseService.getFileHandle(result.resultsFileHandleId)
      content <- synapseService.downloadFile(result.resultsFileHandleId)
    } yield {
      println(fileHandle)
      println(content)
    }
    result(future, timeout)

    result(synapseService.prolongSession, timeout)

    val csvFuture = synapseService.getTableAsCsv(tableId2)
    println(result(csvFuture, timeout))
  }
}

object TestSynapseService extends GuiceBuilderRunnable[TestSynapseService] with App { run }