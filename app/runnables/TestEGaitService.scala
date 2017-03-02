package runnables

import javax.inject.Inject

import services.EGaitServiceFactory

import scala.concurrent.Await.result
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Configuration
import util.ZipFileIterator

import scala.concurrent.Future
import scala.concurrent.duration._

class TestEGaitService @Inject() (
    configuration: Configuration,
    eGaitServiceFactory: EGaitServiceFactory
  ) extends Runnable {

  private val username = configuration.getString("egait.api.username").get
  private val password = configuration.getString("egait.api.password").get
  private val timeout = 10 minutes
  private val adviser = "CHe" // "kf"

  override def run = {
    val eGaitService = eGaitServiceFactory(username, password)

    val future = for {
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

      rawDatas <- Future.sequence(
        searchSessionIds.map( searchSessionId =>
          eGaitService.downloadRawData(proxySessionToken, userSessionId, searchSessionId)
        )
      )

      _ <- eGaitService.logoff(proxySessionToken, userSessionId)
    } yield {
      println("Search session ids:")
      println("-------------------")
      println(searchSessionIds.mkString(", "))
      println("CSVs:")
      println("-------")
      println(csvs.mkString("\n"))
      println("Raw Data:")
      println("--------")

      val fileNames = rawDatas.map( rawData =>
        ZipFileIterator(rawData).map(_._1).mkString(", ")
      )

      println(fileNames.mkString("\n"))
    }

    result(future, timeout)
  }
}

object TestEGaitService extends GuiceBuilderRunnable[TestEGaitService] with App { run }