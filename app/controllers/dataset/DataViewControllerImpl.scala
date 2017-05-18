package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.security.AdaAuthConfig
import com.google.inject.assistedinject.Assisted
import controllers.{DataSetWebContext, JsonFormatter}
import dataaccess.RepoTypes.UserRepo
import dataaccess._
import models._
import models.DataSetFormattersAndIds._
import models.FilterCondition.filterFormat
import models.security.UserManager
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import java.util.Date

import controllers.core.{CrudControllerImpl, HasFormShowEqualEditView, WebContext}
import services.DataSpaceService
import views.html.{dataview => view}

import scala.concurrent.Future
import scala.reflect.ClassTag

trait DataViewControllerFactory {
  def apply(dataSetId: String): DataViewController
}

protected[controllers] class DataViewControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService,
    userRepo: UserRepo,
    val userManager: UserManager
  ) extends CrudControllerImpl[DataView, BSONObjectID](dsaf(dataSetId).get.dataViewRepo)

    with DataViewController
    with AdaAuthConfig
    with HasFormShowEqualEditView[DataView, BSONObjectID] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected lazy val dataViewRepo = dsa.dataViewRepo
  protected lazy val fieldRepo = dsa.fieldRepo

  protected override val listViewColumns = None // Some(Seq("name"))

  private implicit val widgetSpecFormatter = JsonFormatter[WidgetSpec]
  private implicit val eitherFormat = new EitherFormat[Seq[models.FilterCondition], BSONObjectID]
  private implicit val eitherFormatter = JsonFormatter[Either[Seq[models.FilterCondition], BSONObjectID]]

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "filterOrIds" -> seq(of[Either[Seq[models.FilterCondition], BSONObjectID]]),
      "tableColumnNames" -> seq(text),
      "widgetSpecs" -> seq(of[WidgetSpec]),
      "elementGridWidth" -> number(min = 1, max = 12),
      "default" -> boolean,
      "useOptimizedRepoChartCalcMethod" -> boolean
    ) {
       DataView(_, _, _, _, _, _, _, _)
     }
    ((item: DataView) => Some((item._id, item.name, item.filterOrIds, item.tableColumnNames, item.widgetSpecs, item.elementGridWidth, item.default, item.useOptimizedRepoChartCalcMethod)))
  )

  // router for requests; to be passed to views as helper.
  protected val router = new DataViewRouter(dataSetId)
  protected val jsRouter = new DataViewJsRouter(dataSetId)
  protected val dataSetRouter = new DataSetRouter(dataSetId)

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  // create view and data

  override protected type CreateViewData = (String, Form[DataView])

  override protected def getFormCreateViewData(form: Form[DataView]) =
    for {
      dataSetName <- dsa.dataSetName
    } yield
      (dataSetName + " Data View", form)

  override protected[controllers] def createView = { implicit ctx =>
    (view.create(_, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    String,
    BSONObjectID,
    Form[DataView],
    Map[String, Field],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[DataView]
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val nameFieldMapFuture = getNameFieldMap
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val setCreatedByFuture =
      form.value match {
        case Some(dataView) => DataViewRepo.setCreatedBy(userRepo, Seq(dataView))
        case None => Future(())
      }
    for {
      dataSetName <- dataSetNameFuture
      nameFieldMap <- nameFieldMapFuture
      tree <- treeFuture
      _ <- setCreatedByFuture
    } yield
      (dataSetName + " Data View", id, form, nameFieldMap, tree)
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.editNormal(_, _, _, _, _)).tupled
  }

  // list view and data

  override protected type ListViewData = (
    String,
    Page[DataView],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getListViewData(
    page: Page[DataView]
  ) = { request =>
    val setCreatedByFuture = DataViewRepo.setCreatedBy(userRepo, page.items)
    val dataSpaceTreeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val dataSetNameFuture = dsa.dataSetName

    for {
      _ <- setCreatedByFuture
      tree <- dataSpaceTreeFuture
      dataSetName <- dataSetNameFuture
    } yield
      (dataSetName + " Data View", page, tree)
  }

  override protected[controllers] def listView = { implicit ctx =>
    (view.list(_, _, _)).tupled
  }

  // actions

  override def saveCall(
    dataView: DataView)(
    implicit request: Request[AnyContent]
  ): Future[BSONObjectID] =
    for {
      user <- currentUser(request)
      id <- {
        val dataViewWithUser = user match {
          case Some(user) => dataView.copy(timeCreated = new Date(), createdById = user._id)
          case None => throw new AdaException("No logged user found")
        }
        repo.save(dataViewWithUser)
      }
    } yield
      id

  override protected def updateCall(
    dataView: DataView)(
    implicit request: Request[AnyContent]
  ): Future[BSONObjectID] =
    for {
      existingDataViewOption <- repo.get(dataView._id.get)
      id <- existingDataViewOption.map(existingDataView =>
          repo.update(dataView) // .copy(filterOrIds = existingDataView.filterOrIds)
        ).getOrElse(
          Future(dataView._id.get)
        )
    } yield
      id

  override def idAndNames = Action.async { implicit request =>
    for {
      dataViews <- repo.find(
//        sort = Seq(AscSort("name")),
        projection = Seq("name", "default", "elementGridWidth", "timeCreated")
      )
    } yield {
      val sorted = dataViews.toSeq.sortBy(dataView =>
        (!dataView.default, dataView.name)
      )
      val idAndNames = sorted.map( dataView =>
        Json.obj(
          "_id" -> dataView._id,
          "name" -> dataView.name,
          "default" -> dataView.default
        )
      )
      Ok(JsArray(idAndNames))
    }
  }

  override def getAndShowView(id: BSONObjectID) =
    Action.async { implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Entity #$id not found"))
      ) { entity =>
          getEditViewData(id, entity)(request).map(viewData =>
            render {
              case Accepts.Html() => Ok(
                view.edit(
                  viewData._1,
                  viewData._2,
                  viewData._3,
                  viewData._4,
                  viewData._5,
                  router.updateAndShowView
                )
              )
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
          )
      }).recover {
        case t: TimeoutException =>
          Logger.error("Problem found in the edit process")
          InternalServerError(t.getMessage)
      }
    }

  override def updateAndShowView(id: BSONObjectID) =
    Action.async { implicit request =>
      update(id, Redirect(dataSetRouter.getView(id, Nil, Nil, false))).apply(request)
    }

  override def copy(id: BSONObjectID) =
    Action.async { implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Entity #$id not found"))
      ) { dataView =>
        val newDataView = dataView.copy(_id = None, name = dataView.name + " copy", default = false)
        saveCall(newDataView).map { newId =>
          Redirect(router.get(newId)).flashing("success" -> s"Data view '${dataView.name}' has been copied.")
        }
      }
    )
  }

  override def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingFieldNames = filterSpecsOf[DistributionWidgetSpec](dataView).map(_.fieldName)
    val newFieldNames = fieldNames.filter(!existingFieldNames.contains(_))

    if (newFieldNames.nonEmpty) {
      val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ newFieldNames.map(DistributionWidgetSpec(_, None)))
      repo.update(newDataView)
    } else
      Future(())
  }

  override def addDistribution(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = processDataView(dataViewId) { dataView =>
    val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ Seq(DistributionWidgetSpec(fieldName, groupFieldName)))
    repo.update(newDataView)
  }

  override def addCumulativeCounts(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingFieldNames = filterSpecsOf[CumulativeCountWidgetSpec](dataView).map(_.fieldName)
    val newFieldNames = fieldNames.filter(!existingFieldNames.contains(_))

    if (newFieldNames.nonEmpty) {
      val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ newFieldNames.map(CumulativeCountWidgetSpec(_, None)))
      repo.update(newDataView)
    } else
      Future(())
  }

  override def addCumulativeCount(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = processDataView(dataViewId) { dataView =>
    val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ Seq(CumulativeCountWidgetSpec(fieldName, groupFieldName)))
    repo.update(newDataView)
  }

  override def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingFieldNames = filterSpecsOf[BoxWidgetSpec](dataView).map(_.fieldName)
    val newFieldNames = fieldNames.filter(!existingFieldNames.contains(_))

    if (newFieldNames.nonEmpty) {
      val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ newFieldNames.map(BoxWidgetSpec(_)))
      repo.update(newDataView)
    } else
      Future(())
  }

  override def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupFieldName: Option[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingXYZNames = filterSpecsOf[ScatterWidgetSpec](dataView).map { spec =>
      (spec.xFieldName, spec.yFieldName, spec.groupFieldName)
    }
    val fieldNames = (xFieldName, yFieldName, groupFieldName)
    if (!existingXYZNames.contains(fieldNames)) {
      val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ Seq(ScatterWidgetSpec(xFieldName, yFieldName, groupFieldName)))
      repo.update(newDataView)
    } else
      Future(())
  }

  override def addCorrelation(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingNames = filterSpecsOf[CorrelationWidgetSpec](dataView).map(_.fieldNames)
    if (!existingNames.contains(fieldNames)) {
      val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ Seq(CorrelationWidgetSpec(fieldNames)))
      repo.update(newDataView)
    } else
      Future(())
  }

  override def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingFieldNames = dataView.tableColumnNames
    val filteredFieldNames = fieldNames.filter(!existingFieldNames.contains(_))
    if (filteredFieldNames.nonEmpty) {
      val newDataView = dataView.copy(tableColumnNames = existingFieldNames ++ filteredFieldNames)
      repo.update(newDataView)
    } else {
      Future(())
    }
  }

  private def filterSpecsOf[T <: WidgetSpec](
    dataView: DataView)(
    implicit ev: ClassTag[T]
  ): Seq[T] =
    dataView.widgetSpecs.collect{ case t: T => t}

  protected def processDataView(id: BSONObjectID)(fun: DataView => Future[_]) =
    Action.async { implicit request =>
      for {
        dataView <- repo.get(id)
        response <- dataView match {
          case Some(dataView) => fun(dataView).map(x => Some(x))
          case None => Future(None)
        }
      } yield
        response.fold(
          NotFound(s"Data view '#${id.stringify}' not found")
        ) { _ => Ok("Done")}
  }

  override def saveFilter(
    dataViewId: BSONObjectID,
    filterOrIds: Seq[Either[Seq[models.FilterCondition], BSONObjectID]]
  ) = processDataView(dataViewId) { dataView =>
    val newDataView = dataView.copy(filterOrIds = filterOrIds)
    repo.update(newDataView)
  }

  private def getNameFieldMap: Future[Map[String, Field]] =
    fieldRepo.find().map { _.map( field =>
        (field.name, field)
      ).toMap
    }
}