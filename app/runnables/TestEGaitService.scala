package runnables

import javax.inject.Inject

import services.EGaitServiceFactory

import scala.concurrent.Await.result
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Configuration

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

      connectTokenForSearch <-
        eGaitService.getConnectionToken("MilifeSession", proxySessionToken)

      connectTokenForLogin <-
        eGaitService.getConnectionToken("AuthenticationService", proxySessionToken)

      userSessionId <-
        eGaitService.login(connectTokenForLogin)

      searchSessionId <-
        eGaitService.searchSession(connectTokenForSearch, userSessionId, adviser)

      connectTokenForDownload <-
        eGaitService.getConnectionToken("MilifeRest", proxySessionToken)

      parameters <-
        eGaitService.downloadParametersAsCSV(connectTokenForDownload, userSessionId, searchSessionId)

      connectTokenForLogOff <-
        eGaitService.getConnectionToken("AuthenticationService", proxySessionToken)

      _ <- eGaitService.logoff(connectTokenForLogOff, userSessionId)
    } yield {
      println("Search session id:")
      println("------------------")
      println(searchSessionId)
      println("Params:")
      println("-------")
      print(parameters)
    }

    result(future, timeout)
  }
}

object TestEGaitService extends GuiceBuilderRunnable[TestEGaitService] with App { run }