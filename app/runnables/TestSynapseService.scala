package runnables

import javax.inject.Inject

import models.synapse.{RowReference, ColumnType, RowReferenceSet}
import services.SynapseServiceFactory
import scala.concurrent.Await.result
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Configuration
import play.api.libs.json.Json
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
      fileHandle <- {
        println(result.resultsFileHandleId)
        synapseService.getFileHandle(result.resultsFileHandleId)
      }
      content <- synapseService.downloadFile(result.resultsFileHandleId)
    } yield {
      println(fileHandle)
      println(content)
    }
    result(future, timeout)

    result(synapseService.prolongSession, timeout)

    val csvFuture = synapseService.getTableAsCsv(tableId2)
    println(result(csvFuture, timeout))

    val jsonsFuture =
      for {
        fileHandleColumns <-
          synapseService.getTableColumnModels(tableId2).map { columnModels =>
            println(columnModels.results.map(_.columnType).mkString(","))
            columnModels.results.filter(_.columnType == ColumnType.FILEHANDLEID).map(_.toSelectColumn)
          }
        tableFileHandleResults <- {
          val rows = Seq(RowReference(0,0), RowReference(1,1), RowReference(2,2), RowReference(3,3))
          val rowReferenceSet = RowReferenceSet(fileHandleColumns,  None, tableId2, rows)
          synapseService.getTableColumnFileHandles(rowReferenceSet)
        }
        fileContents <- {
          val columnId = fileHandleColumns.map(_.id).head
          val rows = Seq(RowReference(0,0), RowReference(1,1), RowReference(2,2), RowReference(3,3))

          val futures = rows.map { row =>
            synapseService.downloadColumnFile(tableId2, columnId, row.rowId, row.versionNumber)
          }
          Future.sequence(futures)
        }
      } yield
        fileContents.map { fileContent =>
          Json.prettyPrint(Json.parse(fileContent))
        }

    println(result(jsonsFuture, timeout).mkString("\n\n"))
//    synapseService.getTableColumnFileHandles()
  }
}

object TestSynapseService extends GuiceBuilderRunnable[TestSynapseService] with App { run }