package runnables.luxpark

import javax.inject.Inject
import org.incal.core.runnables.{FutureRunnable, InputFutureRunnable, RunnableHtmlOutput}
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.ada.server.services.importers.EGaitServiceFactory

import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class GetEGaitData @Inject() (
    configuration: Configuration,
    eGaitServiceFactory: EGaitServiceFactory
  ) extends InputFutureRunnable[GetEGaitDataSpec] with RunnableHtmlOutput {

  private val username = configuration.getString("egait.api.username").get
  private val password = configuration.getString("egait.api.password").get
  private val certificateFileName = configuration.getString("egait.api.certificate.path").get
  private val baseUrl = configuration.getString("egait.api.rest.url").get

  private val logger = Logger

  override def runAsFuture(input: GetEGaitDataSpec) = {
    val eGaitService = eGaitServiceFactory(username, password, baseUrl)

    for {
      proxySessionToken <- eGaitService.getProxySessionToken(certificateFileName)

      userSessionId <- eGaitService.login(proxySessionToken)

      searchSessionIds <- eGaitService.searchSessions(proxySessionToken, userSessionId)

      csvs <- Future.sequence(
        searchSessionIds.map( searchSessionId =>
          eGaitService.downloadParametersAsCSV(proxySessionToken, userSessionId, searchSessionId)
        )
      )

      kineticDatas <-
        if (input.withRawData)
          Future.sequence(
            searchSessionIds.map( searchSessionId =>
              eGaitService.downloadRawDataStructured(proxySessionToken, userSessionId, searchSessionId)
            )
          )
        else
          Future(Nil)

      _ <- eGaitService.logoff(proxySessionToken, userSessionId)
    } yield {
      addParagraphAndLog("Search session ids:")
      addParagraphAndLog("-------------------")
      addParagraphAndLog(searchSessionIds.mkString(", "))
      addParagraphAndLog("CSVs:")
      addParagraphAndLog("-------")
      csvs.foreach(addParagraphAndLog)

      if (input.withRawData) {
        addParagraphAndLog("Kinetic (Raw) Data:")
        addParagraph("Check the log file for more...")
        logger.info("-------------------")

        val kineticDataStrings = kineticDatas.flatten.map { kineticData =>
          s"""
            ${kineticData.sessionId}, ${kineticData.personId}, ${kineticData.instructor}, ${kineticData.startTime}, ${kineticData.testName},
            ${kineticData.rightSensorFileName}, ${kineticData.leftSensorFileName}

            Right Accelerometer

            x: ${kineticData.rightAccelerometerPoints.take(10).map(_.x).mkString(", ")}
            y: ${kineticData.rightAccelerometerPoints.take(10).map(_.y).mkString(", ")}
            z: ${kineticData.rightAccelerometerPoints.take(10).map(_.z).mkString(", ")}

            Right Gyroscope

            x: ${kineticData.rightGyroscopePoints.take(10).map(_.x).mkString(", ")}
            y: ${kineticData.rightGyroscopePoints.take(10).map(_.y).mkString(", ")}
            z: ${kineticData.rightGyroscopePoints.take(10).map(_.z).mkString(", ")}

            Left Accelerometer

            x: ${kineticData.leftAccelerometerPoints.take(10).map(_.x).mkString(", ")}
            y: ${kineticData.leftAccelerometerPoints.take(10).map(_.y).mkString(", ")}
            z: ${kineticData.leftAccelerometerPoints.take(10).map(_.z).mkString(", ")}

            Left Gyroscope

            x: ${kineticData.leftGyroscopePoints.take(10).map(_.x).mkString(", ")}
            y: ${kineticData.leftGyroscopePoints.take(10).map(_.y).mkString(", ")}
            z: ${kineticData.leftGyroscopePoints.take(10).map(_.z).mkString(", ")}
          """
        }

        logger.info(kineticDataStrings.mkString("\n"))
      }
    }
  }

  protected def addParagraphAndLog(message: String) = {
    logger.info(message)
    addParagraph(message)
  }

  override def inputType = typeOf[GetEGaitDataSpec]
}

case class GetEGaitDataSpec(
  withRawData: Boolean
)