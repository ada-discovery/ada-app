package runnables.mpower

import javax.inject.Inject
import org.ada.server.models.synapse._
import org.incal.play.GuiceRunnableApp
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import org.ada.server.services.importers.SynapseServiceFactory

import scala.concurrent.Await.result
import scala.concurrent.Future
import scala.concurrent.duration._

class TestSynapseService @Inject() (
    configuration: Configuration,
    synapseServiceFactory: SynapseServiceFactory
  ) extends Runnable {

  private val username = configuration.getString("synapse.api.username").get
  private val password = configuration.getString("synapse.api.password").get
  private val tableId1 = "syn6126231"
  private val tableId2 = "syn6126230"
  private val timeout = 10 minutes

  override def run = {
    val synapseService = synapseServiceFactory(username, password)

    val future = for {
      jobToken <- synapseService.startCsvTableQuery(tableId1, s"SELECT * FROM $tableId1")
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
        fileHandleIdContents <- {
          val fileHandleIds = tableFileHandleResults.rows.map(_.list).flatten.map(_.id)
          synapseService.downloadTableFilesInBulk(fileHandleIds, tableId2, None)
        }
      } yield {
        fileHandleIdContents.foreach{ case (fileName, content) =>
          println(fileName)
          println(content)
        }
        fileContents.map { fileContent =>
          Json.prettyPrint(Json.parse(fileContent))
        }
      }

    result(jsonsFuture, timeout)

  }
}

object TestSynapseService extends GuiceRunnableApp[TestSynapseService]