package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.security.AdaAuthConfig
import com.google.inject.assistedinject.Assisted
import controllers.{JsonFormatter, CrudControllerImpl}
import dataaccess.RepoTypes.UserRepo
import dataaccess._
import models._
import models.DataSetFormattersAndIds._
import models.FilterCondition.filterFormat
import models.security.UserManager
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import persistence.dataset.{DataSpaceMetaInfoRepo, DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Action, RequestHeader, Request}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import java.util.Date
import views.html.dataview

import scala.concurrent.Future
import scala.reflect.ClassTag

trait DataViewControllerFactory {
  def apply(dataSetId: String): DataViewController
}

protected[controllers] class DataViewControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    userRepo: UserRepo,
    val userManager: UserManager
  ) extends CrudControllerImpl[DataView, BSONObjectID](dsaf(dataSetId).get.dataViewRepo) with DataViewController with AdaAuthConfig {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected lazy val dataSetName = result(dsa.metaInfo).name
  protected lazy val dataViewRepo = dsa.dataViewRepo
  protected lazy val fieldRepo = dsa.fieldRepo


  protected override val listViewColumns = None // Some(Seq("name"))

  private implicit val statsCalcSpecFormatter = JsonFormatter[StatsCalcSpec]
  private implicit val eitherFormat = new EitherFormat[Seq[models.FilterCondition], BSONObjectID]
  private implicit val eitherFormatter = JsonFormatter[Either[Seq[models.FilterCondition], BSONObjectID]]

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "filterOrIds" -> seq(of[Either[Seq[models.FilterCondition], BSONObjectID]]),
      "tableColumnNames" -> seq(text),
      "statsCalcSpecs" -> seq(of[StatsCalcSpec]),
      "elementGridWidth" -> number(min = 1, max = 12),
      "default" -> boolean,
      "useOptimizedRepoChartCalcMethod" -> boolean
    ) {
       DataView(_, _, _, _, _, _, _, _)
     }
    ((item: DataView) => Some((item._id, item.name, item.filterOrIds, item.tableColumnNames, item.statsCalcSpecs, item.elementGridWidth, item.default, item.useOptimizedRepoChartCalcMethod)))
  )

  // router for requests; to be passed to views as helper.
  protected lazy val router = new DataViewRouter(dataSetId)
  protected lazy val jsRouter = new DataViewJsRouter(dataSetId)
  protected lazy val dataSetRouter = new DataSetRouter(dataSetId)

  override protected lazy val home =
    Redirect(router.plainList)

  override protected def createView(f: Form[DataView])(implicit msg: Messages, request: Request[_]) =
    dataview.create(
      dataSetName + " Data View",
      f,
      router,
      dataSetRouter.allFields
    )

  override protected def showView(id: BSONObjectID, f: Form[DataView])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f: Form[DataView])(implicit msg: Messages, request: Request[_]) = {
    val nameFieldMap = result(getNameFieldMap)

    // TODO: stay in the future
    if(f.value.isDefined)
      result(DataViewRepo.setCreatedBy(userRepo, Seq(f.get)))

    dataview.editNormal(
      dataSetName + " Data View",
      id,
      f,
      router,
      dataSetRouter.allFields,
      nameFieldMap,
      result(dataSpaceTree)
    )
  }

  override protected def listView(page: Page[DataView])(implicit msg: Messages, request: Request[_]) = {
    // TODO: stay in the future
    result(DataViewRepo.setCreatedBy(userRepo, page.items))

    dataview.list(
      dataSetName + " Data View",
      page,
      router,
      jsRouter,
      result(dataSpaceTree)
    )
  }

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
      dataViews <- repo.find(sort = Seq(AscSort("name")))
    } yield {
      val sorted = dataViews.toSeq.sortBy(dataView => (!dataView.default, dataView.name))
      Ok(Json.toJson(sorted))
    }
  }

  override def getAndShowView(id: BSONObjectID) =
    Action.async { implicit request =>
      editCall(id).map(_.fold(
        NotFound(s"Entity #$id not found")
      ) { entity =>
        implicit val msg = messagesApi.preferred(request)
        val nameFieldMap = result(getNameFieldMap)

        render {
          case Accepts.Html() => Ok(
            dataview.edit(
              dataSetName + " Data View",
              id,
              fillForm(entity),
              router,
              router.updateAndShowView,
              dataSetRouter.allFields,
              nameFieldMap,
              result(dataSpaceTree)
            )
          )
          case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
        }
      }).recover {
        case t: TimeoutException =>
          Logger.error("Problem found in the edit process")
          InternalServerError(t.getMessage)
      }
    }

  override def updateAndShowView(id: BSONObjectID) =
    Action.async { implicit request =>
      update(id, Redirect(dataSetRouter.getView(id, 0, "", Left(Nil), false))).apply(request)
    }

  override def copy(id: BSONObjectID) =
    Action.async { implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Entity #$id not found"))
      ) { dataView =>
        implicit val msg = messagesApi.preferred(request)

        val newDataView = dataView.copy(_id = None, name = dataView.name + " copy", default = false)
        saveCall(newDataView).map { newId =>
          Redirect(router.get(newId)).flashing("success" -> s"Data view '${dataView.name}' has been copied.")
        }
      }
    )
  }

  def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingFieldNames = filterSpecsOf[DistributionCalcSpec](dataView).map(_.fieldName)
    val newFieldNames = fieldNames.filter(!existingFieldNames.contains(_))

    if (newFieldNames.nonEmpty) {
      val newDataView = dataView.copy(statsCalcSpecs = dataView.statsCalcSpecs ++ newFieldNames.map(DistributionCalcSpec(_, None)))
      repo.update(newDataView)
    } else
      Future(())
  }

  def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingFieldNames = filterSpecsOf[BoxCalcSpec](dataView).map(_.fieldName)
    val newFieldNames = fieldNames.filter(!existingFieldNames.contains(_))

    if (newFieldNames.nonEmpty) {
      val newDataView = dataView.copy(statsCalcSpecs = dataView.statsCalcSpecs ++ newFieldNames.map(BoxCalcSpec(_)))
      repo.update(newDataView)
    } else
      Future(())
  }


  def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupFieldName: Option[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingXYZNames = filterSpecsOf[ScatterCalcSpec](dataView).map { spec =>
      (spec.xFieldName, spec.yFieldName, spec.groupFieldName)
    }
    val fieldNames = (xFieldName, yFieldName, groupFieldName)
    if (!existingXYZNames.contains(fieldNames)) {
      val newDataView = dataView.copy(statsCalcSpecs = dataView.statsCalcSpecs ++ Seq(ScatterCalcSpec(xFieldName, yFieldName, groupFieldName)))
      repo.update(newDataView)
    } else
      Future(())
  }

  def addCorrelation(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingNames = filterSpecsOf[CorrelationCalcSpec](dataView).map(_.fieldNames)
    if (!existingNames.contains(fieldNames)) {
      val newDataView = dataView.copy(statsCalcSpecs = dataView.statsCalcSpecs ++ Seq(CorrelationCalcSpec(fieldNames)))
      repo.update(newDataView)
    } else
      Future(())
  }

  def addTableFields(
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

  private def filterSpecsOf[T <: StatsCalcSpec](
    dataView: DataView)(
    implicit ev: ClassTag[T]
  ): Seq[T] =
    dataView.statsCalcSpecs.collect{ case t: T => t}

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
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = processDataView(dataViewId) { dataView =>
    val newDataView = dataView.copy(filterOrIds = Seq(filterOrId))
    repo.update(newDataView)
  }

  private def getNameFieldMap: Future[Map[String, Field]] =
    fieldRepo.find().map { _.map( field =>
        (field.name, field)
      ).toMap
    }

  private def dataSpaceTree =
    DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)
}