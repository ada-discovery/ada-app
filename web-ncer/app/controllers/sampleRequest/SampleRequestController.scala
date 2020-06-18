package controllers.sampleRequest

import akka.stream.Materializer
import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import javax.ws.rs.BadRequestException
import org.ada.web.controllers.core.AdaBaseController
import org.incal.core.FilterCondition
import org.incal.play.security.AuthAction
import play.api.libs.json.{JsNumber, JsObject}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import services.SampleRequestService

import scala.concurrent.ExecutionContext.Implicits.global



class SampleRequestController @Inject()(
  sampleRequestService: SampleRequestService
)(
  implicit materializer: Materializer
) extends AdaBaseController {

  def catalogueItems: Action[AnyContent] = Action.async { implicit request =>
    for {
      items <- sampleRequestService.getCatalogueItems
    } yield {
      val json = JsObject(
        items map { case (name, id) =>
          name -> JsNumber(id)
        }
      )
      Ok(json)
    }
  }

  def submitRequest(
    catalogueItemId: Int,
    dataSetId: String,
    tableColumnNames: Seq[String],
    filter: Seq[FilterCondition],
    selectedIds: Seq[BSONObjectID]
  ): Action[AnyContent] = AuthAction { implicit  request =>
    for {
      user <- getUserForRequest()
      csv <- sampleRequestService.createCsv(dataSetId, filter, tableColumnNames, selectedIds)
      _ <- sampleRequestService.sendToRems(csv, catalogueItemId, user)
    } yield {
      Ok("")
    }
  }

  private def getUserForRequest()(implicit request: AuthenticatedRequest[_]) =
    for {
      deadBoltUserOption <- currentUser()
      deadBoltUser = deadBoltUserOption.getOrElse(throw new BadRequestException("Request has no user associated with it."))
    } yield deadBoltUser.user

}
