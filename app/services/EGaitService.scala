package services

import javax.inject.Inject
import javax.net.ssl.SSLContext

import com.google.inject.assistedinject.Assisted
import com.ning.http.client.AsyncHttpClientConfigBean
import play.api.libs.ws.{WSAuthScheme, WSClient, WSRequest, WSResponse}
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.ning.NingWSClient
import org.apache.commons.codec.binary.{Hex, Base64}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.{Codec, Source}

trait EGaitServiceFactory {
  def apply(@Assisted("username") username: String, @Assisted("password") password: String): EGaitService
}

trait EGaitService {

  /**
    * Gets a session token needed for login and all services
    */
  def getProxySessionToken: Future[String]

  /**
    * Gets a connection token used to access a given service
    *
    * @param serviceName
    * @return
    */
  def getConnectionToken(
    serviceName: String,
    sessionToken: String
  ): Future[String]

  /**
    * Logins and returns a user session id
    */
  def login(sessionToken: String): Future[String]

  /**
    * Logoffs
    *
    * @param sessionToken
    * @param userSessionId
    * @return
    */
  def logoff(sessionToken: String, userSessionId: String): Future[Unit]

  /**
    *
    * @param sessionToken
    * @param userSessionId
    */
  def searchSessions(
    sessionToken: String,
    userSessionId: String
  ): Future[Traversable[String]]

  def downloadParametersAsCSV(
    sessionToken: String,
    userSessionId: String,
    searchSessionId: String
  ): Future[String]

  def downloadRawData(
    sessionToken: String,
    userSessionId: String,
    searchSessionId: String
  ): Future[Array[Byte]]
}

protected[services] class EGaitServiceWSImpl @Inject() (
    @Assisted("username") private val username: String,
    @Assisted("password") private val password: String,
//    ws: WSClient,
    configuration: Configuration
  ) extends EGaitService {

  object Url {
    val Base = confValue("egait.api.rest.url")
    val Session = confValue("egait.api.session.url")
    val ServiceConnectionToken = confValue("egait.api.service_connection_token.url")
    val Login = confValue("egait.api.login.url")
    val Logoff = confValue("egait.api.logoff.url")
    val SearchSessions = confValue("egait.api.search_sessions.url")
    val DownloadParametersAsCsvSub1 = confValue("egait.api.download_parameters_as_csv.url.part1")
    val DownloadParametersAsCsvSub2 = confValue("egait.api.download_parameters_as_csv.url.part2")
    val DownloadRawData = confValue("egait.api.download_raw_data.url")
  }

  object ConnectionServiceName {
    val Authentication = "AuthenticationService"
    val SearchSessions = "MilifeSession"
    val CsvDownload = "MilifeRest"
  }

  private val ws = {
    val config = new AsyncHttpClientConfigBean
    config.setAcceptAnyCertificate (true)
    config.setFollowRedirect (true)
    NingWSClient(config)
  }

  private val logger = Logger
  private val certificateFileName = confValue("egait.api.certificate.path")

  private def confValue(key: String) = configuration.getString(key).get

  override def getProxySessionToken: Future[String] = {
    val certificateContent = loadFileAsBase64(certificateFileName)

    val request = ws.url(Url.Base + Url.Session).withHeaders(
      "Content-Type" -> "application/octet-stream",
      "Content-Length" -> certificateContent.length.toString
    )

    request.post(certificateContent).map(
      _.header("session-token").get
    )
  }

  override def getConnectionToken(
    serviceName: String,
    sessionToken: String
  ): Future[String] = {
    val request = ws.url(Url.Base + Url.ServiceConnectionToken + serviceName).withHeaders(
      "session-token" -> sessionToken
    )

    request.get.map(
      _.header("connect-token").get
    )
  }

  override def login(sessionToken: String): Future[String] = {
    val loginInfoXML =
      s"""
        <LoginWithClientInfo xmlns="http://tempuri.org/">
        <userName>${username}</userName>
        <passwordHash>${password}</passwordHash>
        <clientData xmlns:a="http://schemas.datacontract.org/2004/07/AstrumIT.Meditalk.Platform.Core.Interfaces.Security" xmlns:i="http://www.w3.org/2001/XMLSchema-instance">
        <a:ClientId>5c11a22b-fd41-41eb-ad8b-af307a3bfb88</a:ClientId>
        <a:ClientName>Dres med Maria von Witwenkind (Erlangen)</a:ClientName>
        <a:CustomData></a:CustomData>
        <a:WindowsUser>dkpeters</a:WindowsUser></clientData></LoginWithClientInfo>
      """

    for {
      // get the connection token for the authentication service
      connectionToken <-
        getConnectionToken(ConnectionServiceName.Authentication, sessionToken)

      // login and obtain a user session id
      userSessionId <- {
        val request =
          withXmlContent(
            withConnectionToken(connectionToken)(
              ws.url(Url.Base + Url.Login)
            )
          )

        request.post(loginInfoXML).map { response =>
          (response.xml \\ "SessionId").text
        }
      }
    } yield
      userSessionId
  }

  override def logoff(
    sessionToken: String,
    userSessionId: String
  ): Future[Unit] = {
    val logoffInfoXML = s"""<Logoff xmlns="http://tempuri.org/s"><sessionId>${userSessionId}</sessionId></Logoff>"""

    for {
      // get the connection token for the authentication service
      connectionToken <-
        getConnectionToken(ConnectionServiceName.Authentication, sessionToken)

      // logs off
      _ <- {
        val request =
          withXmlContent(
            withConnectionToken(connectionToken)(
              withUserSessionId(userSessionId)(
                ws.url(Url.Base + Url.Logoff)
              )
            )
          )

        request.post(logoffInfoXML).map(_ => ())
      }
    } yield ()
  }

  override def searchSessions(
    sessionToken: String,
    userSessionId: String
  ): Future[Traversable[String]] = {
    val findAllFilterXML =
       """
          <Session xmlns="http://schemas.datacontract.org/2004/07/AstrumIT.MiLife.Server.MiLifeWcfRestServiceInterface.DataModel.SearchSession" xmlns:i="http://www.w3.org/2001/XMLSchema-instance">
          </Session>
       """

    for {
      // get the connection token for the authentication service
      connectionToken <-
        getConnectionToken(ConnectionServiceName.SearchSessions, sessionToken)

      // search sessions and obtain the ids
      searchSessionIds <- {
        val request =
          withXmlContent(
            withConnectionToken(connectionToken)(
              withUserSessionId(userSessionId)(
                ws.url(Url.Base + Url.SearchSessions)
              )
            )
          )

        request.post(findAllFilterXML).map( response =>
          (response.xml \\ "Session" \ "SessionId").map(_.text)
        )
      }
    } yield
      searchSessionIds
  }

  override def downloadParametersAsCSV(
    sessionToken: String,
    userSessionId: String,
    searchSessionId: String
  ): Future[String] =
    for {
      // get the connection token for the authentication service
      connectionToken <-
        getConnectionToken(ConnectionServiceName.CsvDownload, sessionToken)

      // get the csv (content)
      csv <- {
        val request =
          withConnectionToken(connectionToken)(
            withUserSessionId(userSessionId)(
              ws.url(Url.Base + Url.DownloadParametersAsCsvSub1 + searchSessionId + Url.DownloadParametersAsCsvSub2)
            )
          )

        request.get.map(_.body)
      }
    } yield
      csv

  override def downloadRawData(
    sessionToken: String,
    userSessionId: String,
    searchSessionId: String
  ): Future[Array[Byte]] =
    for {
      // get the connection token for the authentication service
      connectionToken <-
        getConnectionToken(ConnectionServiceName.CsvDownload, sessionToken)

      // get the csv (content)
      csv <- {
        val request =
          withConnectionToken(connectionToken)(
            withUserSessionId(userSessionId)(
              ws.url(Url.Base + Url.DownloadRawData + searchSessionId)
            )
          )

        request.get.map(_.bodyAsBytes)
      }
    } yield
      csv

  private def withXmlContent(request: WSRequest): WSRequest =
    request.withHeaders("Content-Type" -> "application/xml")

  private def withConnectionToken(connectionToken: String)(request: WSRequest): WSRequest =
    request.withHeaders("connect-token" -> connectionToken)

  private def withUserSessionId(userSessionId: String)(request: WSRequest): WSRequest =
    request.withHeaders("user-session" -> userSessionId)

  private def withRequestTimeout(timeout: Duration)(request: WSRequest): WSRequest =
    request.withRequestTimeout(timeout.toMillis)

  private def loadFileAsBase64(fileName : String):String = {
    val source = scala.io.Source.fromFile(fileName, "ISO-8859-1")
    val byteArray = source.map(_.toByte).toArray
    source.close()
    val encoded = Base64.encodeBase64(byteArray)
    new String(encoded, "ASCII")
  }
}