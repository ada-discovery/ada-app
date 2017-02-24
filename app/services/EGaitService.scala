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
  def login(connectionToken: String): Future[String]

  /**
    * Logoffs
    *
    * @param connectionToken
    * @param userSessionId
    * @return
    */
  def logoff(connectionToken: String, userSessionId: String): Future[Unit]

  /**
    *
    * @param connectionToken
    * @param userSessionId
    * @param adviserId
    */
  def searchSession(
    connectionToken: String,
    userSessionId: String,
    adviserId: String
  ): Future[String]

  def downloadParametersAsCSV(
    connectionToken: String,
    userSessionId: String,
    searchSessionId: String
  ): Future[String]
}

protected[services] class EGaitServiceWSImpl @Inject() (
    @Assisted("username") private val username: String,
    @Assisted("password") private val password: String,
//    ws: WSClient,
    configuration: Configuration
  ) extends EGaitService {

  object Url {
    val base = confValue("egait.api.rest.url")
    val session = confValue("egait.api.session.url")
    val serviceConnectionToken = confValue("egait.api.service_connection_token.url")
    val login = confValue("egait.api.login.url")
    val logoff = confValue("egait.api.logoff.url")
    val searchSessions = confValue("egait.api.search_sessions.url")
    val downloadParametersAsCsvSub1 = confValue("egait.api.download_parameters_as_csv.url.part1")
    val downloadParametersAsCsvSub2 = confValue("egait.api.download_parameters_as_csv.url.part2")
  }

  private val ws = {
    val config = new AsyncHttpClientConfigBean
    config.setAcceptAnyCertificate (true)
    config.setFollowRedirect (true)
    NingWSClient(config)
  }

  private val logger = Logger
  private val timeout = 10 minutes
  private val certificateFileName = confValue("egait.api.certificate.path")
  private var sessionToken: Option[String] = None

  private def confValue(key: String) = configuration.getString(key).get

  override def getProxySessionToken: Future[String] = {
    val certificateContent = loadFileAsBase64(certificateFileName)

    val request = ws.url(Url.base + Url.session).withHeaders(
      "Content-Type" -> "application/octet-stream",
      "Content-Length" -> certificateContent.length.toString
    )

    request.post(certificateContent).map(
      _.header("session-token").get
    )
  }

  private def loadFileAsBase64(fileName : String):String = {
    val source = scala.io.Source.fromFile(fileName, "ISO-8859-1")
    val byteArray = source.map(_.toByte).toArray
    source.close()
    val encoded = Base64.encodeBase64(byteArray)
    new String(encoded, "ASCII")
  }

  override def getConnectionToken(
    serviceName: String,
    sessionToken: String
  ): Future[String] = {
    val request = ws.url(Url.base + Url.serviceConnectionToken + serviceName).withHeaders(
      "session-token" -> sessionToken
    )

    request.get.map(
      _.header("connect-token").get
    )
  }

  override def login(connectionToken: String): Future[String] = {
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

    SSLContext.getDefault

    val request = ws.url(Url.base + Url.login).withHeaders(
      "Content-Type" -> "application/xml",
      "connect-token" -> connectionToken
    )

    //  namespaces ={"a": "http://schemas.datacontract.org/2004/07/AstrumIT.Meditalk.Platform.Core.Interfaces.Security"}
    //  return xml.find(".//a:SessionId", namespaces).text

    request.post(loginInfoXML).map { response =>
      (response.xml \\ "SessionId").text
    }
  }

  override def logoff(
    connectionToken: String,
    userSessionId: String
  ): Future[Unit] = {
    val logoffInfoXML = s"""<Logoff xmlns="http://tempuri.org/s"><sessionId>${userSessionId}</sessionId></Logoff>"""

    val request = ws.url(Url.base + Url.logoff).withHeaders(
      "Content-Type" -> "application/xml",
      "connect-token" -> connectionToken,
      "user-session" -> userSessionId
    )

    request.post(logoffInfoXML).map(_ => ())
  }

  override def searchSession(
    connectionToken: String,
    userSessionId: String,
    adviserId: String
  ): Future[String] = {
    val filterXML =
       """
         <Session xmlns="http://schemas.datacontract.org/2004/07/AstrumIT.MiLife.Server.MiLifeWcfRestServiceInterface.DataModel.SearchSession" xmlns:i="http://www.w3.org/2001/XMLSchema-instance">
         </Session>
       """
//             <Tag>60217</Tag>
//      s"""
//        <Session xmlns="http://schemas.datacontract.org/2004/07/AstrumIT.MiLife.Server.MiLifeWcfRestServiceInterface.DataModel.SearchSession" xmlns:i="http://www.w3.org/2001/XMLSchema-instance">
//          <EndDate>2017-10-10T00:00:00</EndDate>
//          <Note/>
//          <PersonNo />
//          <SearchTestComments>true</SearchTestComments>
//          <StartDate>2015-09-14T00:00:00</StartDate>
//          <Adviser>XXX</Adviser>
//          <Tag>SID0302</Tag>
//	      </Session>
//      """
    // <Tag>60217</Tag>
    //           <Adviser>${adviserId}</Adviser>


    val request = ws.url(Url.base + Url.searchSessions).withHeaders(
      "Content-Type" -> "application/xml",
      "connect-token" -> connectionToken,
      "user-session" -> userSessionId
    )

    //  namespaces ={"ml":"http://schemas.datacontract.org/2004/07/AstrumIT.MiLife.Server.MiLifeWcfRestServiceInterface.DataModel.SearchSession"}
    //  return xml.find("ml:Session/ml:SessionId", namespaces).text

    request.post(filterXML).map { response =>
      (response.xml \\ "Session" \ "SessionId").text
    }
  }

  override def downloadParametersAsCSV(
    connectionToken: String,
    userSessionId: String,
    searchSessionId: String
  ): Future[String] = {
    val request = ws.url(Url.base + Url.downloadParametersAsCsvSub1 + searchSessionId + Url.downloadParametersAsCsvSub2).withHeaders(
      "connect-token" -> connectionToken,
      "user-session" -> userSessionId
    )

    request.get.map { response =>
      println(response.allHeaders.mkString("\n"))
      response.body
    }
  }

//  def withSessionToken(request: WSRequest): WSRequest =
//    request.withHeaders("sessionToken" -> getSessionToken)

  def withJsonContent(request: WSRequest): WSRequest =
    request.withHeaders("Content-Type" -> "application/json")

  def withRequestTimeout(timeout: Duration)(request: WSRequest): WSRequest =
    request.withRequestTimeout(timeout.toMillis)
}