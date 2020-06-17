package controllers.sampleRequest

import akka.stream.Materializer
import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.incal.core.{ConditionType, FilterCondition}
import org.incal.play.controllers.BaseController
import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import services.SampleRequestService
import org.incal.core.dataaccess.Criterion.Infix

import scala.concurrent.ExecutionContext.Implicits.global



class SampleRequestController @Inject()(
  sampleRequestService: SampleRequestService
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
    tableColumnNames: Seq[String],
    filter: Seq[FilterCondition],
    selectedIds: Seq[BSONObjectID]
  ) = Action.async { implicit request =>
    val extraFilter = FilterCondition(JsObjectIdentity.name, None, ConditionType.In, Some(selectedIds.mkString(",")), None)

    for {
      csv <- sampleRequestService.createCsv("#TODO", filter :+ extraFilter, tableColumnNames)
      _ <- sampleRequestService.sendToRems(csv, catalogueItemId)
    } yield {
      Ok("")
    }
  }

}
