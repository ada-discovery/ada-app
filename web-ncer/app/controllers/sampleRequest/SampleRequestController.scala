package controllers.sampleRequest

import akka.stream.Materializer
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.server.services.UserManager
import org.incal.core.{ConditionType, FilterCondition}
import org.incal.play.controllers.BaseController
import play.api.libs.json.{JsNumber, JsObject}
import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.ws.rs.BadRequestException
import org.ada.server.AdaException
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import services.SampleRequestService

import scala.concurrent.ExecutionContext.Implicits.global



class SampleRequestController @Inject()(
  sampleRequestService: SampleRequestService,
  userManager: UserManager
)(
  implicit materializer: Materializer
) extends BaseController {

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
  )(
    implicit request: AuthenticatedRequest[_]
  ): Action[AnyContent] = Action.async {
    val extraFilter = FilterCondition(JsObjectIdentity.name, None, ConditionType.In, Some(selectedIds.mkString(",")), None)
    for {
      deadBoltUserOption <- currentUser()
      deadBoltUser = deadBoltUserOption.getOrElse(throw new BadRequestException("Request has no user associated with it."))
      userOption <- userManager.findById(deadBoltUser.identifier)
      user = userOption.getOrElse(throw new AdaException("Failed to lookup user information."))
      csv <- sampleRequestService.createCsv(dataSetId, filter :+ extraFilter, tableColumnNames)
      _ <- sampleRequestService.sendToRems(csv, catalogueItemId, user)
    } yield {
      Ok("")
    }
  }

}
