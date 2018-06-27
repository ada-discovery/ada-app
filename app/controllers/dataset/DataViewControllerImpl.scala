package controllers.dataset

import java.util.concurrent.TimeoutException
import java.{util => ju}
import javax.inject.Inject

import _root_.security.AdaAuthConfig
import com.google.inject.assistedinject.Assisted
import controllers.{DataSetWebContext, EnumFormatter, JsonFormatter}
import dataaccess.RepoTypes.UserRepo
import dataaccess._
import models._
import models.DataSetFormattersAndIds._
import models.json.EitherFormat
import models.FilterCondition.{FilterIdentity, filterFormat}
import models.security.{SecurityRole, UserManager}
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import dataaccess.Criterion._
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

  override protected val listViewColumns = None // Some(Seq("name"))
  override protected val entityNameKey = "dataView"
  override protected def formatId(id: BSONObjectID) = id.stringify

  private implicit val widgetSpecFormatter = JsonFormatter[WidgetSpec]
  private implicit val eitherFormat = EitherFormat[Seq[models.FilterCondition], BSONObjectID]
  private implicit val eitherFormatter = JsonFormatter[Either[Seq[models.FilterCondition], BSONObjectID]]
  private implicit val widgetGenerationMethodFormatter = EnumFormatter(WidgetGenerationMethod)

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> nonEmptyText,
      "filterOrIds" -> seq(of[Either[Seq[models.FilterCondition], BSONObjectID]]),
      "tableColumnNames" -> seq(text),
      "widgetSpecs" -> seq(of[WidgetSpec]),
      "elementGridWidth" -> default(number(min = 1, max = 12), 3),
      "default" -> boolean,
      "isPrivate" -> boolean,
      "generationMethod" -> of[WidgetGenerationMethod.Value]
    ) {
       DataView(_, _, _, _, _, _, _, _, _)
     }
    ((item: DataView) => Some((item._id, item.name, item.filterOrIds, item.tableColumnNames, item.widgetSpecs, item.elementGridWidth, item.default, item.isPrivate, item.generationMethod)))
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
    Map[BSONObjectID, String],
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

    val filtersFuture =
      form.value match {
        case Some(dataView) =>
          val filterIds = dataView.widgetSpecs.map(_.subFilterId).flatten
          if (filterIds.nonEmpty) {
            // TODO: IN criterion with BSONObjectID does not work here
            dsa.filterRepo.find(
//              criteria = Seq(FilterIdentity.name #-> filterIds),
              projection = Seq("name")
            )
          } else
            Future(Nil)
        case None => Future(Nil)
      }

    for {
      dataSetName <- dataSetNameFuture
      nameFieldMap <- nameFieldMapFuture
      tree <- treeFuture
      filters <- filtersFuture
      _ <- setCreatedByFuture
    } yield {
      val idFilterNameMap = filters.map( filter => (filter._id.get, filter.name.getOrElse(""))).toMap
      (dataSetName + " Data View", id, form, nameFieldMap, idFilterNameMap, tree)
    }
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.editNormal(_, _, _, _, _, _)).tupled
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
      id <- {
        val mergedDataView =
          existingDataViewOption.fold(dataView) { existingDataView =>
            dataView.copy(createdById = existingDataView.createdById, timeCreated = existingDataView.timeCreated)
          }
        repo.update(mergedDataView)
      }
    } yield
      id

  override def idAndNames = Action.async { implicit request =>
    for {
      dataViews <- repo.find(
//        sort = Seq(AscSort("name")),
        projection = Seq("name", "default", "elementGridWidth", "timeCreated", "generationMethod")
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

  override def idAndNamesAccessible = Action.async { implicit request =>
    // auxiliary function to find data views for given criteria
    def findAux(criteria: Seq[Criterion[Any]]) = repo.find(
      criteria = criteria,
      projection = Seq("name", "default", "elementGridWidth", "timeCreated", "isPrivate", "createdById", "generationMethod")
    )

    for {
      user <- currentUser(request)

      dataViews <- {
        user match {
          case None => Future(Nil)

          case Some(user) =>
            val isAdmin = user.roles.contains(SecurityRole.admin)
            // admin     => get all; non-admin => not private or owner
            if (isAdmin)
              findAux(Nil)
            else
              findAux(Nil).map { views =>
                views.filter { view =>
                  !view.isPrivate || (view.createdById.isDefined && view.createdById.equals(user._id))
                }
              }
              // TODO: fix Apache ignite to support boolean conditions

//              findAux(Seq("isPrivate" #== falsefid)).flatMap { nonPrivateViews =>
//                findAux(Seq("createdById" #== user._id)).map { ownedViews =>
//                  (nonPrivateViews ++ ownedViews).toSet
//                }
//              }
        }
      }
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
                  viewData._6,
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
      for {
        // get the data view
        dataView <- repo.get(id)

        // copy and save the new view
        newId <- dataView.fold(
          Future(Option.empty[BSONObjectID])
        ) { dataView =>
          val newDataView = dataView.copy(_id = None, name = dataView.name + " copy", default = false, timeCreated = new ju.Date)
          saveCall(newDataView).map(Some(_))
        }
      } yield {
        newId.fold(
          NotFound(s"Entity #$id not found")
        ) { newId =>
          Redirect(router.get(newId)).flashing("success" -> s"Data view '${dataView.get.name}' has been copied.")
        }
      }
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
      val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ newFieldNames.map(BoxWidgetSpec(_, None)))
      repo.update(newDataView)
    } else
      Future(())
  }

  override def addBoxPlot(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = processDataView(dataViewId) { dataView =>
    val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ Seq(BoxWidgetSpec(fieldName, groupFieldName)))
    repo.update(newDataView)
  }

  override def addBasicStats(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingFieldNames = filterSpecsOf[BasicStatsWidgetSpec](dataView).map(_.fieldName)
    val newFieldNames = fieldNames.filter(!existingFieldNames.contains(_))

    if (newFieldNames.nonEmpty) {
      val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ newFieldNames.map(BasicStatsWidgetSpec(_)))
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

  override def addIndependenceTest(
    dataViewId: BSONObjectID,
    fieldName: String,
    inputFieldNames: Seq[String]
  ) = processDataView(dataViewId) { dataView =>
    val existingNames = filterSpecsOf[IndependenceTestWidgetSpec](dataView).map(_.fieldNames)
    if (!existingNames.contains(Seq(fieldName) ++ inputFieldNames)) {
      val newDataView = dataView.copy(widgetSpecs = dataView.widgetSpecs ++ Seq(IndependenceTestWidgetSpec(fieldName, inputFieldNames)))
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