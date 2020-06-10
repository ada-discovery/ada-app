package controllers.sampleRequest

import akka.stream.Materializer
import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import org.incal.play.controllers.BaseController
import play.api.mvc.{Action, AnyContent}
import services.SampleRequestService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class SampleRequestController @Inject()(
  @Assisted val dataSetId: String,
  sampleRequestService: SampleRequestService
)(
  implicit materializer: Materializer
) extends BaseController {



//  def submitRequestForFilteredTable(
//    tableColumnNames: Seq[String],
//    filter: Seq[FilterCondition],
//    selectedOnly: Boolean,
//    selectedIds: Seq[BSONObjectID]
//  ) = Action.async { implicit request =>
//    val extraCriteria = if (selectedOnly)
//      Seq(JsObjectIdentity.name #-> selectedIds)
//    else
//      Nil
//
//    for {
//      dataSetSetting <- dsa.setting
//      result <- {
//        exportToCsv(
//          "request.csv",
//          "\t"
//        )(
//          tableColumnNames,
//          dataSetSetting.exportOrderByFieldName,
//          filter,
//          extraCriteria,
//          false,
//          Map[String, FieldType[_]]()
//        ).apply(request)
//      }
//      result.
//    } yield {
//
//    }
//
//  }

}
