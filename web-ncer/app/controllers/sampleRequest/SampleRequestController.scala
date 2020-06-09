package controllers.sampleRequest

import akka.stream.Materializer

import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.field.FieldType
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.web.controllers.core.ExportableAction
import org.incal.core.FilterCondition
import org.incal.play.controllers.BaseController
import org.incal.core.dataaccess.Criterion.Infix
import play.api.libs.json.JsObject
import play.api.mvc.Action
import reactivemongo.bson.BSONObjectID

class SampleRequestController @Inject()(
  @Assisted val dataSetId: String,
  dsaf: DataSetAccessorFactory
)(
  implicit materializer: Materializer
) extends BaseController {

  private val dsa: DataSetAccessor = dsaf(dataSetId).get


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
