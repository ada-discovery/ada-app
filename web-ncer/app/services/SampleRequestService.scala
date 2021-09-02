package services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.Inject
import models.sampleRequest._
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.{FieldTypeId, User}
import org.ada.server.{AdaException, AdaParseException}
import org.ada.web.services.oidc.AccessResourceService
import org.incal.core.dataaccess.Criterion._
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.Results
import services.BatchOrderRequestRepoTypes.SampleRequestSettingRepo

import javax.ws.rs.core.HttpHeaders
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/**
 * Service providing functionality to submit requests to Podium
 */
class SampleRequestService @Inject() (
  dsaf: DataSetAccessorFactory,
  sampleRequestSettingRepo: SampleRequestSettingRepo,
  config: Configuration,
  ws: WSClient,
  accessResourceService: AccessResourceService) {

  private val podiumApiUrl = config.getString("podium.apiUrl").getOrElse(
    throw new AdaException("Configuration issue: 'podium.apiUrl' was not found in the configuration file.")
  )

  /**
   * Create a valid CSV which can be attached to an Podium application
   *
   * @param user User submitting request
   * @param sampleRequest Sample request information
   * @return A string representing a valid CSV file.
   */
  def createCsvByOrganisation(
    user: User,
    sampleRequest: SampleRequest
  ): Future[List[(OrganisationRepresentation, String)]] = {
    val SEPARATOR = "\t"
    val fieldCriteria = if (sampleRequest.tableColumnNames.nonEmpty) Vector(FieldIdentity.name #-> sampleRequest.tableColumnNames) else Nil
    val selectCriteria = if (sampleRequest.selectedIds.nonEmpty) Vector(JsObjectIdentity.name #-> sampleRequest.selectedIds) else Nil
    val dataSetId = sampleRequest.dataSetId
    for {
      dsa <- dsaf.getOrError(dataSetId)
      sampleRequestSetting <- sampleRequestSettingRepo.find(Seq("dataSetId" #== dataSetId))
      fields <- dsa.fieldRepo.find(fieldCriteria)
      items <- dsa.dataSetRepo.find(selectCriteria)
      organisations <- getOrganisations(user)
    } yield {
      val organisationIdentifier =
        if(sampleRequestSetting.nonEmpty)
          sampleRequestSetting.head.organisationFieldIdName
        else throw new AdaException(s"Not found sample request settings for dataset '$dataSetId'")

      val header = fields.map(_.name)
      val itemsByOrg = items.groupBy(item => organisations((item \ organisationIdentifier).as[Long]))
      itemsByOrg.map(itemOrg => {
        val csv = new StringBuilder("")
        csv ++= header.mkString(SEPARATOR)
        csv += '\n'
        itemOrg._2 foreach { item =>
          val row = fields map { field => {
            val res = (item \ field.name).toOption
            if (res.isDefined && field.fieldTypeSpec.fieldType.compare(FieldTypeId.Enum) == 0) {
              field.fieldTypeSpec.enumValues(res.get.as[Int])
            } else
              res.getOrElse(JsNull).toString
            }
          }
          csv ++= row.mkString(SEPARATOR)
          csv += '\n'
        }
        (itemOrg._1, csv.toString())
      }).toList
    }
  }

  def sendToPodium(csvByOrg: List[(OrganisationRepresentation, String)],
                   user: User): Future[List[String]] = {
    Future.sequence(csvByOrg.map(csvOrg => sendRequest(csvOrg._1, csvOrg._2, user)))
  }

  private def sendRequest(orgRep: OrganisationRepresentation,
                          csv: String,
                          user: User): Future[String] = {
    for {
      draft <- createDraft(user)
      updatedDraft <- updateDraft(user, draft, orgRep)
      attachmentInfo <- addAttachment(user, updatedDraft, csv)
      _ <- setAttachmentType(user, attachmentInfo)
      submittedDraft <- submitDraft(user, updatedDraft)
    } yield {
      s"/#/requests/detail/${submittedDraft.uuid}"
    }
  }


  private def createDraft(user: User): Future[RequestRepresentation] = {

    def createPodiumDraft(authHeader: String) = {
      ws.url(s"$podiumApiUrl/api/requests/drafts")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .post(Results.EmptyContent())
    }

    for {
      res <- accessResourceService.accessResource(user, createPodiumDraft)
    } yield
      parseRequestRepresentation(res)
  }

  private def updateDraft(user: User,
                  requestRep: RequestRepresentation,
                  organisationRep: OrganisationRepresentation): Future[RequestRepresentation] = {


    def createDummyRequestRepresentation(requestRep: RequestRepresentation,
                                         orgRep: OrganisationRepresentation): RequestRepresentation = {

      val principalInvestigatorUpdated = requestRep.requestDetail.principalInvestigator
        .copy(name = Option("AutomaticGenName"),
          email = Option("AutomaticGenEmail@uni.lu"),
          jobTitle = Option("Doctor"),
          affiliation = Option("AutomaticGenAffiliation"))

      val requestDetailUpdated = requestRep.requestDetail
        .copy(title = Option("AutomaticGenRequest"),
          background = Option("AutomaticGenBackground"),
          researchQuestion = Option("AutomaticGenQuestion"),
          hypothesis = Option("AutomaticGenHypthesis"),
          methods = Option("AutomaticGenMethods"),
          relatedRequestNumber = Option("AutomaticGenRequestNumber"),
          principalInvestigator = principalInvestigatorUpdated,
          requestType =Option(Seq(RequestType.Material)),
          searchQuery = Option("AutomaticGenSearchQuery")
        )

      requestRep.copy(organisations = List(orgRep), requestDetail = requestDetailUpdated)

    }

    def updatePodiumDraft(authHeader: String, body: JsValue) = {
      ws.url(s"$podiumApiUrl/api/requests/drafts")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .put(body)
    }

    val dummyRequestRep = createDummyRequestRepresentation(requestRep, organisationRep)

    for {
      res <- accessResourceService.accessResource(user, Json.toJson(dummyRequestRep), updatePodiumDraft)
    } yield
      parseRequestRepresentation(res)
  }

  private def submitDraft(user: User,
                  updatedDraftRep: RequestRepresentation): Future[RequestRepresentation] = {

    def submitPodiumDraft(authHeader: String, body: String = "", requestRep: RequestRepresentation) = {
      ws.url(s"$podiumApiUrl/api/requests/drafts/${requestRep.uuid}/submit")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .get()
    }

    for {
      res <- accessResourceService.accessResource(
        user = user,
        requestBody = null,
        additionalParam = updatedDraftRep,
        callResource = submitPodiumDraft
      )
    } yield {
      res.json.validate[List[RequestRepresentation]] match {
        case r: JsSuccess[List[RequestRepresentation]] => r.get.head
        case e: JsError => throw AdaParseException("Error parsing RequestRepresentation Json", new Throwable(JsError.toJson(e).toString()))
      }
    }
  }

  private def addAttachment(user: User,
                            requestRep: RequestRepresentation, csv: String): Future[RequestFileRepresentation] = {

    def addAttachmentToPodiumDraft(authHeader: String, csv: String, requestRep: RequestRepresentation) = {
      ws.url(s"$podiumApiUrl/api/requests/${requestRep.uuid}/files")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader).post(
        Source(
          Vector(
            FilePart("file", "samples.csv", Option("text/csv"), Source(Vector(ByteString(csv))))
          )
        )
      )
    }

    for {
      res <- accessResourceService.accessResource(user, csv, requestRep, addAttachmentToPodiumDraft)
    } yield {
      parseRequestFileRepresentation(res)
    }

  }

  private def setAttachmentType(user: User, requestFileRep: RequestFileRepresentation): Future[RequestFileRepresentation] = {

    def setPodiumFileType(authHeader: String, body: JsValue, requestFileRep: RequestFileRepresentation) = {
      ws.url(s"$podiumApiUrl/api/requests/${requestFileRep.request.uuid}/files/${requestFileRep.uuid}/type")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .put(body)
    }

    val requestFileUpdated = requestFileRep.copy(requestFileType = RequestFileType.OTHER)

    for {
      res <- accessResourceService.accessResource(user, Json.toJson(requestFileUpdated), requestFileUpdated, setPodiumFileType)
    } yield
      parseRequestFileRepresentation(res)
  }

  private def parseRequestFileRepresentation(response: WSResponse): RequestFileRepresentation = {
    response.json.validate[RequestFileRepresentation] match {
      case r: JsSuccess[RequestFileRepresentation] => r.get
      case e: JsError => throw AdaParseException("Error parsing RequestFileRepresentation Json", new Throwable(JsError.toJson(e).toString()))
    }
  }

  private def parseRequestRepresentation(response: WSResponse): RequestRepresentation = {
    response.json.validate[RequestRepresentation] match {
      case r: JsSuccess[RequestRepresentation] => r.get
      case e: JsError => throw AdaParseException("Error parsing RequestRepresentation Json", new Throwable(JsError.toJson(e).toString()))
    }
  }

  /**
    * Get available organisation in podium
    * @param user
    * @return
    */
  private def getOrganisations(user: User): Future[Map[Long, OrganisationRepresentation]] = {

    def getPodiumOrganisations(authHeader: String) = {
      ws.url(s"$podiumApiUrl/api/organisations/available")
        .withHeaders(HttpHeaders.AUTHORIZATION -> authHeader)
        .get()
    }

    for {
      res <- accessResourceService.accessResource(user, getPodiumOrganisations)
    } yield {
      res.json.validate[List[OrganisationRepresentation]] match {
        case s: JsSuccess[List[OrganisationRepresentation]] => s.get.map(org => org.id.get -> org).toMap
        case e: JsError => throw AdaParseException("Error parsing OrganisationRepresentation Json", new Throwable(JsError.toJson(e).toString()))
      }
    }
  }

}
