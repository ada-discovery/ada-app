package runnables

import javax.inject.Inject

import services.EGaitServiceFactory

import scala.concurrent.Await.result
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.{Configuration, Play}
import org.incal.core.util.ZipFileIterator

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

      kineticDatas <- Future.sequence(
        searchSessionIds.map( searchSessionId =>
          eGaitService.downloadRawDataStructured(proxySessionToken, userSessionId, searchSessionId)
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
      println("Kinetic (Raw) Data:")
      println("-------------------")

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

      println(kineticDataStrings.mkString("\n"))
    }

    result(future, timeout)
  }
}

object TestEGaitService extends GuiceBuilderRunnable[TestEGaitService] with App { run }