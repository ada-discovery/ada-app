package services.datasetimporter

import java.util.Date
import javax.inject.Inject

import dataaccess.{FieldTypeFactory, FieldTypeInferrerFactory}
import dataaccess.FieldTypeHelper._
import models.EGaitDataSetImport
import play.api.Configuration
import services.{EGaitService, EGaitServiceFactory}

import scala.concurrent.Await._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private class EGaitDataSetImporter @Inject()(
    eGaitServiceFactory: EGaitServiceFactory,
    configuration: Configuration
  ) extends AbstractDataSetImporter[EGaitDataSetImport] {

  private val delimiter = ','
  private val eol = "\r\n"
  private val username = configuration.getString("egait.api.username").get
  private val password = configuration.getString("egait.api.password").get
  private val saveBatchSize = 100

  // Field type inferrer
  private val nullValueAliases = Set("", "-")
  private val fti = {
    val ftf = FieldTypeFactory(nullValueAliases, dateFormats, displayDateFormat, arrayDelimiter)
    val ftif = FieldTypeInferrerFactory(ftf, maxEnumValuesCount, minAvgValuesPerEnum, arrayDelimiter)
    ftif.apply
  }

  override def apply(importInfo: EGaitDataSetImport): Future[Unit] = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    val eGaitService = eGaitServiceFactory(username, password)
    val dsa = createDataSetAccessor(importInfo)

    try {
      for {
        csvs <- {
          logger.info("Downloading CSV table from eGait...")
          getSessionCsvs(eGaitService)
        }

        lines: Iterator[String] =
          csvs.map(_.split(eol)) match {
            case Nil => Seq[String]().iterator
            case csvLines =>
              // skip the header from all but the first csv
              val tailLines = csvLines.tail.map(_.tail).flatten
              val all = csvLines.head ++ tailLines
              println(all.mkString("\n"))
              all.iterator
          }

        // collect the column names
        columnNames =  dataSetService.getColumnNames(delimiter.toString, lines)

        // parse lines
        values = {
          logger.info(s"Parsing lines...")
          dataSetService.parseLines(columnNames, lines, delimiter.toString, true)
        }

        _ <- saveDataAndDictionaryWithTypeInference(dsa, columnNames, values, Some(saveBatchSize), Some(fti))
      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def getSessionCsvs(
    eGaitService: EGaitService
  ): Future[Traversable[String]] =
    for {
      proxySessionToken <-
        eGaitService.getProxySessionToken

      userSessionId <-
        eGaitService.login(proxySessionToken)

      searchSessionIds <-
        eGaitService.searchSessions(proxySessionToken, userSessionId)

      csvs <- Future.sequence(
        searchSessionIds.map( searchSessionId =>
          eGaitService.downloadParametersAsCSV(proxySessionToken, userSessionId, searchSessionId)
        )
      )

      _ <- eGaitService.logoff(proxySessionToken, userSessionId)
    } yield
      csvs
}