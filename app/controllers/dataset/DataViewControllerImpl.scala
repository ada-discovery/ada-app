package controllers.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import controllers.{JsonFormatter, CrudControllerImpl}
import dataaccess.{AscSort, Criterion}
import models._
import models.DataSetFormattersAndIds._
import models.FilterCondition.filterFormat
import Criterion.CriterionInfix
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Action, RequestHeader, Request}
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import java.util.Date
import views.html.dataview

trait DataViewControllerFactory {
  def apply(dataSetId: String): DataViewController
}

protected[controllers] class DataViewControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends CrudControllerImpl[DataView, BSONObjectID](dsaf(dataSetId).get.dataViewRepo) with DataViewController {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected lazy val dataSetName = result(dsa.metaInfo).name
  protected lazy val dataViewRepo = dsa.dataViewRepo

  protected override val listViewColumns = None // Some(Seq("name"))

  private implicit val fieldChartTypeFormatter = JsonFormatter[FieldChartType]
  private implicit val filterFormatter = JsonFormatter[Filter]

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "default" -> boolean,
      "filters" -> seq(of[Filter]),
      "tableColumnNames" -> seq(text),
      "chartTypes" -> seq(of[FieldChartType])
    ) { (id, name, default, filters, tableColumnNames, chartTypes) =>
      DataView(id, name, default, filters, tableColumnNames, chartTypes)
    }
    ((item: DataView) => Some((item._id, item.name, item.default, item.filters, item.tableColumnNames, item.chartTypes)))
  )

  // router for requests; to be passed to views as helper.
  protected lazy val router: DataViewRouter = new DataViewRouter(dataSetId)
  protected lazy val dataSetRouter: DataSetRouter = new DataSetRouter(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  override protected def createView(f : Form[DataView])(implicit msg: Messages, request: Request[_]) =
    dataview.create(dataSetName + " Data View", f, router)

  override protected def showView(id: BSONObjectID, f : Form[DataView])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[DataView])(implicit msg: Messages, request: Request[_]) = {
    dataview.edit(
      dataSetName + " Data View",
      id,
      f,
      router,
      dataSetRouter.fieldNames,
      result(dataSpaceMetaInfoRepo.find())
    )
  }

  override protected def listView(page: Page[DataView])(implicit msg: Messages, request: Request[_]) =
    dataview.list(
      dataSetName + " Data View",
      page,
      router,
      result(dataSpaceMetaInfoRepo.find())
    )
}