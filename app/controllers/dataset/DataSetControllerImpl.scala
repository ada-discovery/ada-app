package controllers.dataset

import java.util.UUID
import java.{util => ju}
import javax.inject.Inject

import security.AdaAuthConfig

import scala.collection.mutable.{Map => MMap}
import _root_.util.{FieldUtil, GroupMapList}
import dataaccess.JsonUtil._
import models.ConditionType._
import _root_.util.WebExportUtil._
import _root_.util.{seqFutures, shorten}
import dataaccess._
import dataaccess.FilterRepoExtra._
import models.{MultiChartDisplayOptions, _}
import com.google.inject.assistedinject.Assisted
import controllers.{routes, _}
import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import Criterion.Infix
import controllers.core._
import org.apache.commons.lang3.StringEscapeUtils
import models.FilterCondition.FilterOrId
import models.Widget.WidgetWrites
import models.json.{ManifestedFormat, OptionFormat, SubTypeFormat, TupleFormat}
import models.ml._
import persistence.RepoTypes.{ClassificationRepo, RegressionRepo, UnsupervisedLearningRepo}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.{Configuration, Logger}
import play.api.data.{Form, Mapping}
import play.api.data.Forms.{mapping, number, of, optional}
import play.api.libs.json._
import play.api.mvc.Results.{Redirect, _}
import play.api.data.Forms.{mapping, _}
import reactivemongo.bson.BSONObjectID
import services._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Filter => _, _}
import reactivemongo.play.json.BSONFormats._
import services.ml.MachineLearningService
import views.html.dataset
import models.ml.DataSetTransformation._
import models.security.{SecurityRole, UserManager}
import services.stats.calc.{ChiSquareResult, IndependenceTestResult, OneWayAnovaResult}
import services.stats.StatsService
import IndependenceTestResult.independenceTestResultFormat
import be.objectify.deadbolt.scala.AuthenticatedRequest
import field.{FieldType, FieldTypeHelper}
import models.FilterConditionExtraFormats.coreFilterConditionFormat

import scala.math.Ordering.Implicits._
import scala.concurrent.{Future, TimeoutException}

trait GenericDataSetControllerFactory {
  def apply(dataSetId: String): DataSetController
}

protected[controllers] class DataSetControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    statsService: StatsService,
    dataSetService: DataSetService,
    widgetService: WidgetGenerationService,
    classificationRepo: ClassificationRepo,
    regressionRepo: RegressionRepo,
    unsupervisedLearningRepo: UnsupervisedLearningRepo,
    dataSpaceService: DataSpaceService,
    tranSMARTService: TranSMARTService,
    val userManager: UserManager
  ) extends ReadonlyControllerImpl[JsObject, BSONObjectID]

    with DataSetController
    with ExportableAction[JsObject]
    with AdaAuthConfig {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected val fieldRepo = dsa.fieldRepo
  protected val categoryRepo = dsa.categoryRepo
  protected val filterRepo = dsa.filterRepo
  protected val dataViewRepo = dsa.dataViewRepo

  private val logger = Logger // (this.getClass())

  // note that the associated data set repo could be updated (by calling updateDataSetRepo)
  // therefore it should not be stored as val
  override protected def repo = dsa.dataSetRepo

  // TODO: replace with a proper cache
  private val jsonWidgetResponseCache = MMap[String, Future[Seq[JsArray]]]()

  // auto-generated filename for csv files
  protected def csvFileName: String = dataSetId.replace(" ", "-") + ".csv"

  // auto-generated filename for json files
  protected def jsonFileName: String = dataSetId.replace(" ", "-") + ".json"

  // auto-generated filename for tranSMART data files
  protected def tranSMARTDataFileName: String = dataSetId.replace(" ", "-") + "_data_file"

  // auto-generated filename for tranSMART mapping files
  protected def tranSMARTMappingFileName: String = dataSetId.replace(" ", "-") + "_mapping_file"

  // router for requests; to be passed to views as helper.
  protected val router = new DataSetRouter(dataSetId)

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"
  private val numericTypes = Set(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date)

  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private val doubleFieldType = ftf(FieldTypeSpec(FieldTypeId.Double)).asValueOf[Double]

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  private implicit val storageTypeFormatter =  EnumFormatter(StorageType)
  private implicit val seriesProcessingSpec = JsonFormatter[SeriesProcessingSpec]
  private implicit val seriesTransformationSpec = JsonFormatter[SeriesTransformationSpec]

//  private val distScreenWidgetsGenMethod = WidgetGenerationMethod.FullData
  private val distScreenWidgetsGenMethod = WidgetGenerationMethod.RepoAndFullData
  private val independenceTestKeepUndefined = false

  private lazy val fractalisServerUrl = configuration.getString("fractalis.server.url")

  private val resultDataSetMapping: Mapping[ResultDataSetSpec] = mapping(
    "id" -> nonEmptyText,
    "name" -> nonEmptyText,
    "storageType" -> of[StorageType.Value]
  )(ResultDataSetSpec.apply)(ResultDataSetSpec.unapply)

  private val seriesProcessingSpecForm = Form(
    mapping(
      "sourceDataSetId" -> ignored(dataSetId),
      "resultDataSetSpec" -> resultDataSetMapping,
      "seriesProcessingSpecs" -> seq(of[SeriesProcessingSpec]),
      "preserveFieldNames" -> seq(nonEmptyText),
      "processingBatchSize" -> optional(number(min = 1, max = 200)),
      "saveBatchSize" -> optional(number(min = 1, max = 200))
    )(DataSetSeriesProcessingSpec.apply)(DataSetSeriesProcessingSpec.unapply))

  private val seriesTransformationSpecForm = Form(
    mapping(
      "sourceDataSetId" -> ignored(dataSetId),
      "resultDataSetSpec" -> resultDataSetMapping,
      "seriesTransformationSpecs" -> seq(of[SeriesTransformationSpec]),
      "preserveFieldNames" -> seq(nonEmptyText),
      "processingBatchSize" -> optional(number(min = 1, max = 200)),
      "saveBatchSize" -> optional(number(min = 1, max = 200))
    )(DataSetSeriesTransformationSpec.apply)(DataSetSeriesTransformationSpec.unapply))

  override protected def listViewColumns = result(
    dataViewRepo.find().map {
      _.filter(_.default).headOption.map(_.tableColumnNames)
    }
  )

  // list view and data

  override protected type ListViewData = (
    String,
    Page[JsObject],
    Map[String, String],
    Seq[String]
  )

  override protected def getListViewData(
    page: Page[JsObject]
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val fieldLabelMapFuture = getFieldLabelMap(listViewColumns.get)

    for {
      dataSetName <- dataSetNameFuture
      fieldLabelMap <- fieldLabelMapFuture
    } yield
      (dataSetName + " Item", page, fieldLabelMap, listViewColumns.get)
  }

  override protected[controllers] def listView = { implicit ctx =>
    (dataset.list(_, _, _, _)).tupled
  }

  // show view and data

  override type ShowViewData = DataSetShowViewDataHolder

  override protected def getShowViewData(
    id: BSONObjectID,
    item: JsObject
  ) = { request =>
    val dataSetNameFuture = dsa.dataSetName
    val dataSpaceTreeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val fieldsFuture = fieldRepo.find()

    for {
    // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space name
      dataSpaceTree <- dataSpaceTreeFuture

      // get all the fields
      fields <- fieldsFuture
    } yield {
      // create a field name label / renderer map
      val fieldNameLabelAndRendererMap = fields.map { field =>
        val fieldType = ftf(field.fieldTypeSpec)
        (field.name, (field.labelOrElseName, fieldType.jsonToDisplayString(_)))
      }.toMap

      DataSetShowViewDataHolder(
        id,
        dataSetName + " Item",
        item,
        router.getDefaultView,
        true,
        fieldNameLabelAndRendererMap,
        dataSpaceTree
      )
    }
  }

  override protected[controllers] def showView = { implicit ctx => dataset.show(_) }

  override protected def filterValueConverters(
    fieldNames: Traversable[String]
  ): Future[Map[String, String => Option[Any]]] =
    FieldUtil.valueConverters(fieldRepo, fieldNames)

  /**
    * Generate content of csv export file and create download.
    *
    * @param delimiter Delimiter for csv output file.
    * @return View for download.
    */
  override def exportRecordsAsCsv(
    dataViewId: BSONObjectID,
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = {
    val eolToUse = eol match {
      case Some(eol) => if (eol.trim.nonEmpty) eol.trim else csvEOL
      case None => csvEOL
    }

    // TODO: don't use results but stay in future

    val tableFieldNames = result(dataViewTableColumnNames(dataViewId))

    // TODO: introduce a flag for field type conversion (raw vs labels)
    val nameFieldTypeMap: Map[String, FieldType[_]] =
      if (tableFieldNames.nonEmpty && tableColumnsOnly) {
        val fields = result(getFields(tableFieldNames))
        fields.map(field => (field.name, ftf(field.fieldTypeSpec))).toMap
      } else
        Map[String, FieldType[_]]()

    val projection =
      if (tableColumnsOnly) tableFieldNames else Nil

    val headerFieldNames =
      if (tableColumnsOnly)
        tableFieldNames
      else result(fieldRepo.find().map(_.map(_.name).toSeq))

    exportToCsv(
      csvFileName,
      delimiter,
      eolToUse,
      if (replaceEolWithSpace) csvCharReplacements else Nil)(
      result(dsa.setting).exportOrderByFieldName,
      filter,
      projection,
      Some(headerFieldNames.sorted),
      nameFieldTypeMap
    )
  }

  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  override def exportRecordsAsJson(
    dataViewId: BSONObjectID,
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = {
    val fieldNames = result(dataViewTableColumnNames(dataViewId))

    // TODO: introduce a flag for field type conversion (raw vs labels)
    val nameFieldTypeMap: Map[String, FieldType[_]] =
      if (fieldNames.nonEmpty && tableColumnsOnly) {
        val fields = result(getFields(fieldNames))
        fields.map(field => (field.name, ftf(field.fieldTypeSpec))).toMap
      } else
        Map[String, FieldType[_]]()

    exportToJson(
      jsonFileName)(
      result(dsa.setting).exportOrderByFieldName,
      filter,
      if (tableColumnsOnly) fieldNames else Nil,
      nameFieldTypeMap
    )
  }

  private def dataViewTableColumnNames(
    dataViewId: BSONObjectID
  ): Future[Seq[String]] =
    dataViewRepo.get(dataViewId).map {
      _ match {
        case Some(dataView) => dataView.tableColumnNames
        case None => Nil
      }
    }

  private case class ViewResponse(
    count: Int,
    widgets: Traversable[Option[Widget]],
    tableItems: Traversable[JsObject],
    filter: Filter,
    tableFields: Traversable[Field]
  )

  private case class InitViewResponse(
    count: Int,
    tableItems: Traversable[JsObject],
    filter: Filter,
    tableFields: Traversable[Field]
  )

  override def getDefaultView = Action.async { implicit request =>
    for {
    //      // get the default view
    //      defaultView <- dataViewRepo.find(criteria = Seq("default" #== "true"), limit = Some(1)).map(_.headOption)
    //
    //      // if not available pick any view
    //      selectedView <- defaultView match {
    //        case Some(view) => Future(Some(view))
    //        case None => dataViewRepo.find(limit = Some(1)).map(_.headOption)
    //      }
      selectedView <- dataViewRepo.find(sort = Seq(DescSort("default")), limit = Some(1)).map(
        _.headOption
      )
    } yield
      selectedView match {
        case Some(view) => Redirect(router.getView(view._id.get, Nil, Nil, false))
        case None => Redirect(new DataViewRouter(dataSetId).plainList).flashing("errors" -> "No view to show. You must first define one.")
      }
  }

  override def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[PageOrder],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ) = AuthAction { implicit request =>
    val start = new ju.Date()

    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val dataViewFuture = dataViewRepo.get(dataViewId)
    val settingFuture = dsa.setting

    {
      for {
        // get the data set name
        dataSetName <- dataSetNameFuture

        // get the data space tree
        dataSpaceTree <- treeFuture

        // load the view
        dataView <- dataViewFuture

        // setting
        setting <- settingFuture

        // check if the user can edit the view
        canEditView <- dataView.map(canEditView(_, request)).getOrElse(Future(false))

        // initialize filters
        filterOrIdsToUse: Seq[FilterOrId] =
          if (!filterChanged) {
            dataView.map(_.filterOrIds) match {
              case Some(viewFilterOrIds) =>
                val initViewFilterOrIds = if (viewFilterOrIds.nonEmpty) viewFilterOrIds else Seq(Left(Nil))

                val padding = Seq.fill(Math.max(initViewFilterOrIds.size - filterOrIds.size, 0))(Left(Nil))

                (filterOrIds ++ padding).zip(initViewFilterOrIds).map { case (filterOrId, viewFilterOrId) =>
                  if (filterOrId.isLeft && filterOrId.left.get.isEmpty) {
                    viewFilterOrId
                  } else
                    filterOrId
                }

              case None =>
                filterOrIds
            }
          } else
            filterOrIds

        // initialize table pages
        tablePagesToUse = {
          val padding = Seq.fill(Math.max(filterOrIdsToUse.size - tablePages.size, 0))(PageOrder(0, ""))
          tablePages ++ padding
        }

        // table column names and widget specs
        (tableColumnNames, widgetSpecs) = dataView.map(view => (view.tableColumnNames, view.widgetSpecs)).getOrElse((Nil, Nil))

        // load all the filters if needed
        resolvedFilters <- Future.sequence(filterOrIdsToUse.map(filterRepo.resolve))

        // collect the filters' conditions
        conditions = resolvedFilters.map(_.conditions)

        // convert the conditions to criteria
        criteria <- Future.sequence(conditions.map(toCriteria))

        // create a name -> field map of all the referenced fields for a quick lookup
        nameFieldMap <- createNameFieldMap(conditions, widgetSpecs, tableColumnNames)

        // get the response data
        viewResponses <-
          Future.sequence(
            (tablePagesToUse, criteria, resolvedFilters).zipped.map { case (tablePage, criteria, resolvedFilter) =>
              getInitViewResponse(tablePage.page, tablePage.orderBy, resolvedFilter, criteria, nameFieldMap, tableColumnNames)
            }
          )
      } yield {
        Logger.info(s"Data loading of a view for the data set '${dataSetId}' finished in ${new ju.Date().getTime - start.getTime} ms")
        val renderingStart = new ju.Date()
        render {
          case Accepts.Html() => {
            val callbackId = UUID.randomUUID.toString

            val viewPartsWidgetFutures = (viewResponses, tablePagesToUse, criteria).zipped.map {
              case (viewResponse, tablePage, criteria) =>
                val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * pageLimit, viewResponse.count, tablePage.orderBy, Some(viewResponse.filter))
                val viewData = TableViewData(newPage, viewResponse.tableFields)
                val method = dataView.map(_.generationMethod).getOrElse(WidgetGenerationMethod.Auto)
                val widgetsWithFieldsFuture = getViewWidgetsWithFields(widgetSpecs, criteria, nameFieldMap.values, method)

                (viewData, widgetsWithFieldsFuture)
            }

            val viewParts = viewPartsWidgetFutures.map(_._1)
            val jsonsFuture = Future.sequence(viewPartsWidgetFutures.map(_._2)).map { allWidgetsWithFields =>
              val allWidgets = allWidgetsWithFields.map(_.map(_.map(_._1)))
              val minMaxWidgets = if (allWidgets.size > 1) setBoxPlotMinMax(allWidgets) else allWidgets

              val allFieldNames = allWidgetsWithFields.map(_.flatMap(_.map(_._2)))
              minMaxWidgets.map(_.flatten).zip(allFieldNames).map { case (widgets, fieldNames) =>
                widgetsToJsons(widgets.toSeq, fieldNames.toSeq, nameFieldMap)
              }
            }

            jsonWidgetResponseCache.put(callbackId, jsonsFuture)

            val response = Ok(
              dataset.showView(
                dataSetName,
                dataViewId,
                dataView.map(_.name).getOrElse("N/A"),
                viewParts,
                callbackId,
                dataView.map(_.elementGridWidth).getOrElse(3),
                setting.filterShowFieldStyle,
                canEditView,
                dataSpaceTree
              )
            )
            Logger.info(s"Rendering of a view for the data set '${dataSetId}' finished in ${new ju.Date().getTime - renderingStart.getTime} ms")
            response
          }
          case Accepts.Json() => Ok(Json.toJson(viewResponses.head.tableItems))
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the getView process")
        InternalServerError(t.getMessage)
      case e: AdaConversionException => {
        request.headers.get("Referer") match {
          case Some(refererUrl) => Redirect(refererUrl).flashing("errors" -> s"Conversion problem: ${e.getMessage}")
          case None => Redirect(router.getDefaultView).flashing("errors" -> s"Conversion problem: ${e.getMessage}")
        }
      }
    }
  }

  private def createNameFieldMap(
    conditions: Traversable[Traversable[FilterCondition]],
    widgetSpecs: Traversable[WidgetSpec],
    tableColumnNames: Traversable[String]
  ) = {
      // filters' field names
      val filterFieldNames = conditions.flatMap(_.map(_.fieldName.trim))

      // widgets' field names
      val widgetFieldNames = widgetSpecs.flatMap(_.fieldNames)

      getFields((tableColumnNames ++ filterFieldNames ++ widgetFieldNames).toSet).map { fields =>
        fields.map(field => (field.name, field)).toMap
      }
    }

  private def widgetsToJsons(
    widgets: Seq[Widget],
    fieldNames: Seq[Seq[String]],
    fieldMap: Map[String, Field]
  ): JsArray = {
    val jsons = fieldNames.zip(widgets).map { case (fieldNames, widget) =>
      widgetToJson(widget, fieldNames, fieldMap)
    }

    JsArray(jsons)
  }

  private def widgetToJson(
    widget: Widget,
    fieldNames: Seq[String],
    fieldMap: Map[String, Field]
  ): JsValue = {
    val fields = fieldNames.map(fieldMap.get).flatten
    val fieldTypes = fields.map(field => ftf(field.fieldTypeSpec).asValueOf[Any])

    // make a scalar type out of it
    val scalarFieldTypesToUse = fieldTypes.map(fieldType => ftf(fieldType.spec.copy(isArray = false)).asInstanceOf[FieldType[Any]])
    implicit val writes = new WidgetWrites[Any](scalarFieldTypesToUse, Some(doubleFieldType))
    Json.toJson(widget)
  }

  private def canEditView(
    dataView: DataView,
    request: Request[_]
  ): Future[Boolean] =
    for {
      currentUser <- currentUser(request)
    } yield {
      currentUser.map { user =>
        val isAdmin = user.roles.contains(SecurityRole.admin)

        val isOwner =
          dataView.createdById match {
            case Some(createdById) => user._id.get.equals(createdById)
            case None => false
          }

        isAdmin || isOwner
      }.getOrElse(
        false
      )
    }

  override def getWidgets = Action.async { implicit request =>
    val callbackId = request.body.asFormUrlEncoded.flatMap(_.get("callbackId").map(_.head))

    callbackId match {
      case None =>  Future(BadRequest("No callback id provided."))
      case Some(callbackId) =>
        jsonWidgetResponseCache.get(callbackId).map { jsonWidgets =>
          jsonWidgetResponseCache.remove(callbackId)
          jsonWidgets.map(jsons => Ok(JsArray(jsons)))
        }.getOrElse(
          Future(NotFound(s"Widget response callback $callbackId not found."))
        )
    }
  }

  override def getViewElementsAndWidgetCallback(
    dataViewId: BSONObjectID,
    tableOrder: String,
    filterOrId: FilterOrId,
    oldCountDiff: Option[Int]
  ) = Action.async { implicit request =>
    val start = new ju.Date()
    val tablePage = 0

    val dataSetNameFuture = dsa.dataSetName
    val dataViewFuture = dataViewRepo.get(dataViewId)
    val resolvedFilterFuture = filterRepo.resolve(filterOrId)

    {
      for {
        // data set name
        dataSetName <- dataSetNameFuture

        // load the view
        dataView <- dataViewFuture

        // resolved filter
        resolvedFilter <- resolvedFilterFuture

        // criteria
        criteria <- toCriteria(resolvedFilter.conditions)

        (tableColumnNames, widgetSpecs) = dataView.map(view => (view.tableColumnNames, view.widgetSpecs)).getOrElse((Nil, Nil))

        // create a name -> field map of all the referenced fields for a quick lookup
        nameFieldMap <- createNameFieldMap(Seq(resolvedFilter.conditions), widgetSpecs, tableColumnNames)

        // get the init response data
        viewResponse <- getInitViewResponse(tablePage, tableOrder, resolvedFilter, criteria, nameFieldMap, tableColumnNames)
      } yield {
        Logger.info(s"Data loading of a widget panel and a table for the data set '${dataSetId}' finished in ${new ju.Date().getTime - start.getTime} ms")

        // create a widgets-future calculation and register with a callback id
        val method = dataView.map(_.generationMethod).getOrElse(WidgetGenerationMethod.Auto)
        val jsonWidgetsFuture = getViewWidgetsWithFields(widgetSpecs, criteria, nameFieldMap.values, method).map { widgetsWithFields =>
          val widgets = widgetsWithFields.flatMap(_.map(_._1))
          val fieldNames = widgetsWithFields.flatMap(_.map(_._2))
          val jsons = widgetsToJsons(widgets.toSeq, fieldNames.toSeq, nameFieldMap)
          Seq(jsons)
        }
        val widgetsCallbackId = UUID.randomUUID.toString
        jsonWidgetResponseCache.put(widgetsCallbackId, jsonWidgetsFuture)

        render {
          case Accepts.Html() => {
            val newPage = Page(viewResponse.tableItems, tablePage, tablePage * pageLimit, viewResponse.count, tableOrder, Some(viewResponse.filter))

            val pageHeader = messagesApi.apply("list.count.title", oldCountDiff.getOrElse(0) + viewResponse.count, dataSetName + " Item")

            val table = dataset.viewTable(newPage, viewResponse.tableFields, router, true)
            val conditionPanel = views.html.filter.conditionPanel(Some(viewResponse.filter))
            val filterModel = Json.toJson(viewResponse.filter.conditions)

            Ok(Json.obj(
              "table" -> table.toString(),
              "conditionPanel" -> conditionPanel.toString(),
              "filterModel" -> filterModel,
              "count" -> viewResponse.count,
              "pageHeader" -> pageHeader,
              "widgetsCallbackId" -> widgetsCallbackId
            ))
          }
          case Accepts.Json() => Ok(Json.toJson(viewResponse.tableItems))
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the getWidgetPanelAndTable process")
        InternalServerError(t.getMessage)
    }
  }

  override def getTable(
    page: Int,
    orderBy: String,
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val filterFuture = filterRepo.resolve(filterOrId)

    val fieldsFuture = getFields(fieldNames)

    for {
    // use a given filter conditions or load one
      resolvedFilter <- filterFuture

      // get the fields
      fields <- fieldsFuture

      // create a name->field map
      nameFieldMap = fields.map(field => (field.name, field)).toMap

      // resolve criteria
      criteria <- toCriteria(resolvedFilter.conditions)

      // retrieve all the table items
      tableItems <- getTableItems(page, orderBy, criteria, nameFieldMap, fieldNames)

      // get the total count
      count <- repo.count(criteria)
    } yield {
      val tablePage = Page(tableItems, page, page * pageLimit, count, orderBy, Some(resolvedFilter))
      val fieldsInOrder = fieldNames.map(nameFieldMap.get).flatten

      Ok(dataset.viewTable(tablePage, fieldsInOrder, router, true))
    }
  }

  private def setBoxPlotMinMax(
    widgets: Seq[Traversable[Option[Widget]]]
  ): Seq[Traversable[Option[Widget]]] = {
    val widgetsSeqs = widgets.map(_.toSeq)
    val chartCount = widgets.head.size

    def getMinMaxWhiskers[T](index: Int): Option[(T, T)] = {
      val boxWidgets: Seq[BoxWidget[T]] = widgetsSeqs.flatMap { widgets =>
        widgets(index).flatMap(
          _ match {
            case x: BoxWidget[T] => Some(x)
            case _ => None
          }
        )
      }

      boxWidgets match {
        case Nil => None
        case _ =>
          implicit val ordering = boxWidgets.head.ordering
          val minLowerWhisker = boxWidgets.flatMap(_.data.map(_._2.lowerWhisker)).min
          val maxUpperWhisker = boxWidgets.flatMap(_.data.map(_._2.upperWhisker)).max
          Some(minLowerWhisker.asInstanceOf[T], maxUpperWhisker.asInstanceOf[T])
      }
    }

    def setMinMax[T: Ordering](
      boxWidget: BoxWidget[T],
      minMax: (Any, Any)
    ): BoxWidget[T] =
      boxWidget.copy(min = Some(minMax._1.asInstanceOf[T]), max = Some(minMax._2.asInstanceOf[T]))

    val indexMinMaxWhiskers =
      for (index <- 0 until chartCount) yield
        (index, getMinMaxWhiskers[Any](index))

    widgetsSeqs.map { widgets =>
      widgets.zip(indexMinMaxWhiskers).map { case (widgetOption, (index, minMaxWhiskersOption)) =>

        widgetOption.map { widget =>
          minMaxWhiskersOption.map { minMaxWhiskers =>
            widget match {
              case x: BoxWidget[_] =>
                implicit val ordering = x.ordering
                setMinMax(x, minMaxWhiskers)
              case _ => widget
            }
          }.getOrElse(widget)
        }
      }
    }
  }

  private def getViewWidgetsWithFields(
    widgetSpecs: Seq[WidgetSpec],
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field],
    generationMethod: WidgetGenerationMethod.Value
  ): Future[Traversable[Option[(Widget, Seq[String])]]] = {
    // future to load sub-criteria
    val filterSubCriteriaFuture = filterSubCriteriaMap(widgetSpecs)

    for {
      // get the widgets' sub criteria
      filterSubCriteria <- filterSubCriteriaFuture

      widgetsWithFields <- widgetService.applyWithFields(widgetSpecs, repo, criteria, filterSubCriteria.toMap, fields, generationMethod)
    } yield
      widgetsWithFields
  }

  private def getInitViewResponse(
    page: Int,
    orderBy: String,
    filter: Filter,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field],
    tableFieldNames: Seq[String]
  ): Future[InitViewResponse] = {

    // total count
    val countFuture = repo.count(criteria)

    // table items
    val tableItemsFuture = getTableItems(page, orderBy, criteria, nameFieldMap, tableFieldNames)

    for {
      // obtain the total item count satisfying the resolved filter
      count <- countFuture

      // load the table items
      tableItems <- tableItemsFuture
    } yield {
      val tableFields = tableFieldNames.map(nameFieldMap.get).flatten
      val newFilter = setFilterLabels(filter, nameFieldMap)
      InitViewResponse(count, tableItems, newFilter, tableFields)
    }
  }

  private def getTableItems(
    page: Int,
    orderBy: String,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field],
    tableFieldNames: Seq[String]
  ): Future[Traversable[JsObject]] = {
    val tableFieldNamesToLoad = tableFieldNames.filterNot { tableFieldName =>
      nameFieldMap.get(tableFieldName).map(field => field.isArray || field.fieldType == FieldTypeId.Json).getOrElse(false)
    }

    // table items
    if (tableFieldNamesToLoad.nonEmpty)
      getFutureItemsForCriteria(Some(page), orderBy, criteria, tableFieldNamesToLoad ++ Seq(JsObjectIdentity.name), Some(pageLimit))
    else
      Future(Nil)
  }

  override def findCustom(
    filterOrId: FilterOrId,
    orderBy: String,
    projection: Seq[String],
    limit: Option[Int],
    skip: Option[Int]
  ) = Action.async { implicit request =>
    for {
      // use a given filter conditions or load one
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // get the conditions
      conditions = resolvedFilter.conditions

      // generate criteria
      criteria <- toCriteria(conditions)

      // get the items
      items <- repo.find(criteria, toSort(orderBy), projection, limit, skip)
    } yield
      render {
        case Accepts.Html() => BadRequest("Html version of the service is not available.")
        case Accepts.Json() => Ok(Json.toJson(items))
      }
  }

  private def setFilterLabels(
    filter: Filter,
    fieldNameMap: Map[String, Field]
  ): Filter = {
    def valueStringToDisplayString[T](
      fieldType: FieldType[T],
      text: Option[String]
    ): Option[String] =
      text.map { text =>
        val value = fieldType.valueStringToValue(text.trim)
        fieldType.valueToDisplayString(value)
      }

    val newConditions = filter.conditions.map { condition =>
      fieldNameMap.get(condition.fieldName.trim) match {
        case Some(field) => {
          val fieldType = ftf(field.fieldTypeSpec)
          val value = condition.value

          val valueLabel = condition.conditionType match {
            case In | NotIn =>
              value.map(
                _.split(",").flatMap(x => valueStringToDisplayString(fieldType, Some(x))).mkString(", ")
              )

            case _ => valueStringToDisplayString(fieldType, value)
          }
          condition.copy(fieldLabel = field.label, valueLabel = valueLabel)
        }
        case None => condition
      }
    }

    filter.copy(conditions = newConditions)
  }

  private def getFilterFieldNameMap(filter: Filter): Future[Map[String, Field]] =
    getFields(filter.conditions.map(_.fieldName)).map {
      _.map(field => (field.name, field)).toMap
    }

  private def getFields(
    fieldNames: Traversable[String]
  ): Future[Traversable[Field]] =
    if (fieldNames.isEmpty)
      Future(Nil)
    else
      fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSeq))

  private def getValues[T](
    field: Field,
    jsons: Traversable[JsObject]
  ): Traversable[Option[T]] = {
    val typedFieldType = ftf(field.fieldTypeSpec).asValueOf[T]
    val jsonValues = project(jsons, field.name)
    jsonValues.map(typedFieldType.jsonToValue)
  }

  /**
    * Fetches, checks and prepares the specified data fields for a scatterplot.
    * Only compatible FieldTypes (FieldType.Double and FieldType.Integer) are used.
    * Displays the resulting scatterplot in a view.
    *
    * @param xFieldNameOption Name of field to be used for x coordinates.
    * @param yFieldNameOption Name of field to be used for y coordinates.
    * @return View with scatterplot and selection option for different xFieldName and yFieldName.
    */
  override def getScatterStats(
    xFieldNameOption: Option[String],
    yFieldNameOption: Option[String],
    groupFieldNameOption: Option[String],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    dsa.setting.flatMap { setting =>
      // initialize the x, y, and group field names
      val xFieldName: Option[String] =
        xFieldNameOption.map(Some(_)).getOrElse(
          setting.defaultScatterXFieldName
        )

      val yFieldName: Option[String] =
        yFieldNameOption.map(Some(_)).getOrElse(
          setting.defaultScatterYFieldName
        )

      val groupFieldName: Option[String] =
        groupFieldNameOption.map { fieldName =>
          val trimmed = fieldName.trim
          if (trimmed.isEmpty) None else Some(trimmed)
        }.flatten

      getScatterStatsAux(xFieldName, yFieldName, groupFieldName, filterOrId, setting)
    }
  }

  private def getScatterStatsAux(
    xFieldName: Option[String],
    yFieldName: Option[String],
    groupFieldName: Option[String],
    filterOrId: FilterOrId,
    setting: DataSetSetting)(
    implicit request: AuthenticatedRequest[_]
  ): Future[Result] = {

    // auxiliary function to retrieve a field definition
    def getField(fieldName: Option[String]): Future[Option[Field]] =
      fieldName.map(fieldRepo.get).getOrElse(Future(None))

    // field retrieve futures
    val xFieldFuture = getField(xFieldName)
    val yFieldFuture = getField(yFieldName)
    val groupFieldFuture = getField(groupFieldName)

    // other futures
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val filterFuture = filterRepo.resolve(filterOrId)

    // display options
    val displayOptions = BasicDisplayOptions(height = Some(500))

    for {
      // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space tree
      dataSpaceTree <- treeFuture

      // x field
      xField <- xFieldFuture

      // y field
      yField <- yFieldFuture

      // group field
      groupField <- groupFieldFuture

      // use a given filter conditions or load one
      resolvedFilter <- filterFuture

      // get the criteria
      criteria <- toCriteria(resolvedFilter.conditions)

      widget <- {
        // collect all the fields
        val fields = Seq(groupField, xField, yField).flatten
        val startTime = new ju.Date
        (xField zip yField).headOption.map { case (xField, yField) =>
          val widgetSpec = ScatterWidgetSpec(xField.name, yField.name, groupFieldName, None, displayOptions)

          widgetService.genStreamed(widgetSpec, repo, criteria, fields).map { widgets =>
            println(s"Scatter widget generated in ${new ju.Date().getTime - startTime.getTime} ms.")
            widgets.head
          }
        }.getOrElse(
          Future(None)
        )
      }

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
    } yield {
      val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

      render {
        case Accepts.Html() => Ok(dataset.scatterStats(
          dataSetName,
          xField,
          yField,
          groupField,
          widget,
          newFilter,
          setting.filterShowFieldStyle,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("GetScatterStats function doesn't support JSON response.")
      }
    }
  }

  def getDistribution(
    fieldNameOption: Option[String],
    groupFieldNameOption: Option[String],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>

    val groupFieldName: Option[String] =
      groupFieldNameOption.map { fieldName =>
        val trimmed = fieldName.trim
        if (trimmed.isEmpty) None else Some(trimmed)
      }.flatten

    {
      for {
        // get the data set name
        dataSetName <- dsa.dataSetName

        // get the data space tree
        dataSpaceTree <- dataSpaceService.getTreeForCurrentUser(request)

        // use a given filter conditions or load one
        resolvedFilter <- filterRepo.resolve(filterOrId)

        // get the criteria
        criteria <- toCriteria(resolvedFilter.conditions)

        // get the data set setting
        setting <- dsa.setting

        // widget field
        field <- {
          val fieldName = fieldNameOption match {
            case Some(fieldName) => Some(fieldName)
            case None => setting.defaultDistributionFieldName
          }

          fieldName.map(fieldRepo.get).getOrElse(Future(None))
        }

        // get the group field
        groupField <- groupFieldName.map(fieldRepo.get).getOrElse(Future(None))

        // generate widgets - distribution and box
        widgets <- field match {
          case Some(field) =>
            val distChartType =
              if (numericTypes.contains(field.fieldType))
                if (groupField.isDefined) ChartType.Spline else ChartType.Column
              else
                if (groupField.isDefined) ChartType.Column else ChartType.Pie

            val widgetSpecs: Seq[WidgetSpec] = Seq(
              DistributionWidgetSpec(
                fieldName = field.name,
                groupFieldName = groupField.map(_.name),
                displayOptions = MultiChartDisplayOptions(
                  gridWidth = Some(6),
                  height = Some(500),
                  chartType = Some(distChartType)
                )
              ),
              BoxWidgetSpec(
                fieldName = field.name,
                groupFieldName = groupField.map(_.name),
                displayOptions = BasicDisplayOptions(gridWidth = Some(3), height = Some(500))
              ),
              BasicStatsWidgetSpec(
                fieldName = field.name,
                displayOptions = BasicDisplayOptions(gridWidth = Some(3), height = Some(500))
              )
            )
            val start = new ju.Date
            widgetService(widgetSpecs, repo, criteria, Map(), Seq(field) ++ groupField, distScreenWidgetsGenMethod).map { widgets =>
              println(s"Dist. widgets generated in ${new ju.Date().getTime - start.getTime} ms using ${distScreenWidgetsGenMethod.toString} method.")
              widgets
            }
          case None =>
            Future(Nil)
        }

        // create a name -> field map of the filter referenced fields
        fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
      } yield {
        val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

        render {
          case Accepts.Html() => Ok(dataset.distribution(
            dataSetName,
            field,
            groupField,
            widgets.flatten.toSeq,
            newFilter,
            setting.filterShowFieldStyle,
            dataSpaceTree
          ))
          case Accepts.Json() => BadRequest("GetDistribution function doesn't support JSON response.")
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the distribution process")
        InternalServerError(t.getMessage)
    }
  }

  override def getDistributionWidget(
    fieldName: String,
    groupFieldName: Option[String],
    filterId: Option[BSONObjectID]
  ) = Action.async { implicit request =>
    for {
      // get a filter
      resolvedFilter <- filterId match {
        case Some(filterId) => filterRepo.get(filterId)
        case None => Future(None)
      }

      // get the criteria
      criteria <- toCriteria(resolvedFilter.map(_.conditions).getOrElse(Nil))

      // chart field
      field <- fieldRepo.get(fieldName)

      // get the group field
      groupField <- groupFieldName.map(fieldRepo.get).getOrElse(Future(None))

      // generate a distribution widget
      widgetJson <- field match {
        case Some(field) =>
          val widgetSpec = DistributionWidgetSpec(
            fieldName = field.name,
            groupFieldName = groupField.map(_.name),
            displayOptions = MultiChartDisplayOptions(height = Some(500))
          )

          val fields = Seq(field) ++ groupField
          widgetService.genFromRepo(widgetSpec, repo, criteria, fields).map( widgets =>
            widgets.headOption.flatten.map(widgetToJson(_, widgetSpec.fieldNames, fields.map( field => (field.name, field)).toMap))
          )
        case None =>
          Future(None)
      }
    } yield
      widgetJson match {
        case Some(json) => Ok(json)
        case None => BadRequest(s"Field $fieldName couldn't be found.")
      }
  }

  override def getCorrelations(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    val dataSetNameTreeSettingFuture = getDataSetNameTreeAndSetting(request)
    val filterFuture = filterRepo.resolve(filterOrId)
    for {
      // get the data set name, the data space tree, and the setting
      (dataSetName, dataSpaceTree, setting) <- dataSetNameTreeSettingFuture

      // use a given filter conditions or load one
      resolvedFilter <- filterFuture

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
    } yield {
      // get a new fileter
      val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

      render {
        case Accepts.Html() => Ok(dataset.correlation(
          dataSetName,
          newFilter,
          setting.filterShowFieldStyle,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("Correlations function doesn't support JSON response.")
      }
    }
  }.recover {
    case t: TimeoutException =>
      Logger.error("Problem found in the getCorrelations method")
      InternalServerError(t.getMessage)
  }
  }

  override def calcCorrelations(
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val fieldNames = request.body.asFormUrlEncoded.flatMap(_.get("fieldNames[]")).getOrElse(Nil)

    if (fieldNames.isEmpty)
      Future(BadRequest("No input provided."))
    else
      calcCorrelationsAux(fieldNames, filterOrId)
  }

  private def calcCorrelationsAux(
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Future[Result] = {
    for {
      // use a given filter conditions or load one
      resolvedFilter <-  filterRepo.resolve(filterOrId)

      // get the criteria
      criteria <- toCriteria(resolvedFilter.conditions)

      // chart fields
      fields <- getFields(fieldNames)

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)

      // create a correlation widget spec
      widgetSpec = {
        val displayOptions = BasicDisplayOptions(height = Some(Math.max(400, fields.size * 20)))
        CorrelationWidgetSpec(fieldNames, None, displayOptions)
      }

      // generate a widget
      widgets <- widgetService.genStreamed(widgetSpec, repo, criteria, fields)
    } yield {
      val widgetJson = widgets.head.map { widget =>
        // if we have more than 50 fields for performance purposed we round correlation to 3 decimal places
        val newWidget =
          if (fields.size > 50) {
            val heatmapWidget = widget.asInstanceOf[HeatmapWidget]
            val newData = heatmapWidget.data.map(_.map(_.map(value => Math.round(value * 1000).toDouble / 1000)))
            heatmapWidget.copy(data = newData)
          } else
            widget

        widgetToJson(newWidget, widgetSpec.fieldNames, fields.map( field => (field.name, field)).toMap)
      }

      widgetJson match {
        case Some(json) => Ok(json)
        case None => BadRequest(s"Correlation widget cannot be generated.")
      }
    }
  }.recover {
    case t: TimeoutException =>
      Logger.error("Problem found in the getCorrelations method")
      InternalServerError(t.getMessage)
  }

  override def getCumulativeCount(
    fieldNameOption: Option[String],
    groupFieldNameOption: Option[String],
    filterOrId: FilterOrId
  ) = AuthAction { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    // initialize the group name
    val groupFieldName: Option[String] =
      groupFieldNameOption.map { fieldName =>
        val trimmed = fieldName.trim
        if (trimmed.isEmpty) None else Some(trimmed)
      }.flatten

    // auxiliary function to retrieve a field definition
    def getField(fieldName: Option[String]): Future[Option[Field]] =
      fieldName.map(fieldRepo.get).getOrElse(Future(None))

    {
      for {
        // get the data set setting
        setting <- dsa.setting

        // get the data set name
        dataSetName <- dsa.dataSetName

        // get the data space tree
        dataSpaceTree <- dataSpaceService.getTreeForCurrentUser(request)

        // initialize the field name
        fieldName = fieldNameOption.map(Some(_)).getOrElse(
          setting.defaultCumulativeCountFieldName
        )

        // get the field definition
        field <- getField(fieldName)

        // get the group field definition
        groupField <- getField(groupFieldName)

        // use a given filter conditions or load one
        resolvedFilter <- filterRepo.resolve(filterOrId)

        // get the criteria
        criteria <- toCriteria(resolvedFilter.conditions)

        // retrieve the jsons/items with or without a group field and generate the widget
        widgets <-
          field.map { field =>
            val projection = Seq(field.name) ++ groupField.map(_.name)
            repo.find(criteria, Nil, projection.toSet).map { items =>

              val widgetSpec = CumulativeCountWidgetSpec(
                fieldName = field.name,
                groupFieldName = groupFieldName,
                displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Line), height = Some(500))
              )

              widgetService.genFromFullData(widgetSpec, items, Seq(field) ++ groupField)
            }
          }.getOrElse(
            Future(Nil)
          )

        // create a name -> field map of the filter referenced fields
        fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
      } yield {
        val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

        render {
          case Accepts.Html() => Ok(dataset.cumulativeCount(
            dataSetName,
            field,
            groupField,
            widgets.head,
            newFilter,
            setting.filterShowFieldStyle,
            dataSpaceTree
          ))
          case Accepts.Json() => BadRequest("GetCumulativeCount function doesn't support JSON response.")
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the GetCumulativeCount process")
        InternalServerError(t.getMessage)
    }
  }

  def getCategoriesWithFieldsAsTreeNodes(
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val start = new ju.Date()

    val settingFuture = dsa.setting

    val filterFuture = filterRepo.resolve(filterOrId)

    val allCategoriesFuture = categoryRepo.find()

    val fieldsWithCategoryFuture = fieldRepo.find(
      criteria = Seq("categoryId" #!= None),
      projection = Seq("name", "label", "categoryId", "fieldType")
    )

    for {
    // get the data set setting
      setting <- settingFuture

      // get the filter
      filter <- filterFuture

      // retrieve all the categories
      categories <- allCategoriesFuture

      // retrieve all the fields with a category defined
      fieldsWithCategory <- fieldsWithCategoryFuture

      // convert conditions to criteria
      criteria <- toCriteria(filter.conditions)

      // count the not-null elements for each field
      fieldNotNullCounts <-
      if (setting.filterShowNonNullCount) {
        seqFutures(fieldsWithCategory.toSeq.grouped(1000)) { fieldGroup =>
          Future.sequence(
            fieldGroup.map(field =>
              // count not null
              dsa.dataSetRepo.count(criteria ++ Seq(field.name #!@)).map(count => (field, Some(count)))
            )
          )
        }
      } else {
        Future(Seq(fieldsWithCategory.map(field => (field, None))))
      }
    } yield {
      val jsTreeNodes =
        categories.map(JsTreeNode.fromCategory) ++
          fieldNotNullCounts.flatten.map { case (field, count) => JsTreeNode.fromField(field, count) }

      logger.info("Categories with fields and counts retrieved in " + (new ju.Date().getTime - start.getTime) + " ms.")
      Ok(Json.toJson(jsTreeNodes))
    }
  }

  override def getFractalis(
    fieldNameOption: Option[String]
  ) = AuthAction { implicit request => {
      for {
        // get the data set name, data space tree and the data set setting
        (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)

        // field
        field <- {
          val fieldName = fieldNameOption match {
            case Some(fieldName) => Some(fieldName)
            case None => setting.defaultDistributionFieldName
          }

          fieldName.map(fieldRepo.get).getOrElse(Future(None))
        }
      } yield {
        val sessionCookie = request.cookies.get("PLAY2AUTH_SESS_ID")
        val sessionId = sessionCookie.map(_.value)

        //        println("isSecure  : " + sessionCookie.get.secure)
        //        println("HTTP Only : " + sessionCookie.get.httpOnly)
        //        println("Value     : " + sessionCookie.get.value)

        fractalisServerUrl.map { url =>
          sessionId.map { sessionId =>
            render {
              case Accepts.Html() => Ok(dataset.fractalis(
                dataSetName,
                url,
                sessionId,
                field,
                setting.filterShowFieldStyle,
                tree
              ))
              case Accepts.Json() => BadRequest("getFractalis function doesn't support JSON response.")
            }
          }.getOrElse(
            BadRequest("Session id not available.")
          )
        }.getOrElse(
          BadRequest("URL for Fractalis is not available. Set one in a config file with the id \'fractalis.server.url\'.")
        )
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the getFractalis process")
        InternalServerError(t.getMessage)
    }
  }

  override def getClusterization = AuthAction { implicit request => {
    for {
      // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
    } yield {
      render {
        case Accepts.Html() => Ok(dataset.cluster(
          dataSetName,
          setting.filterShowFieldStyle,
          tree
        ))
        case Accepts.Json() => BadRequest("getUnsupervisedLearning function doesn't support JSON response.")
      }
    }
  }.recover {
    case t: TimeoutException =>
      Logger.error("Problem found in the getUnsupervisedLearning process")
      InternalServerError(t.getMessage)
  }
  }

  private def getDataSetNameTreeAndSetting(request: Request[_]): Future[(String, Traversable[DataSpaceMetaInfo], DataSetSetting)] = {
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val settingFuture = dsa.setting

    for {
    // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space tree
      dataSpaceTree <- treeFuture

      // get the data set setting
      setting <- settingFuture
    } yield
      (dataSetName, dataSpaceTree, setting)
  }

  override def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int]
  ) = AuthAction { implicit request =>
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(JsObjectIdentity.name)).toSet.toSeq
      else
        Nil

    val mlModelFuture = unsupervisedLearningRepo.get(mlModelId)
    val criteriaFuture = loadCriteria(filterId)

    for {
      mlModel <- mlModelFuture
      criteria <- criteriaFuture
      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, criteria)
    } yield
      mlModel match {
        case Some(mlModel) =>
          val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
          val jsonsWithStringIds = jsons.map { json =>
            val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
            json.+(JsObjectIdentity.name, JsString(id.stringify))
          }

          val (idClasses, idPCA12s) = mlService.clusterAndGetPCA12(jsonsWithStringIds, fieldNameAndSpecs, mlModel, featuresNormalizationType, pcaDims)

          val numericFields = fields.filter(field => !field.fieldTypeSpec.isArray && numericTypes.contains(field.fieldType))

          val idClassMap = idClasses.toMap

          // PCA 1-2 Scatter
          val displayOptions = BasicDisplayOptions(height = Some(500))

          val pca12WidgetData = idPCA12s.map { case (id, pca12) =>
            (idClassMap.get(id).get.toString, pca12)
          }.toGroupMap.toSeq.sortBy(_._1)

          val pca12Scatter = ScatterWidget("PCA", "PC1", "PC2", "PC1", "PC2", pca12WidgetData, displayOptions)

          val jsonsWithClasses = jsonsWithStringIds.flatMap { json =>
            val stringId = (json \ JsObjectIdentity.name).as[String]
            idClassMap.get(stringId).map(clazz =>  json.+("cluster_class", JsNumber(clazz)))
          }

          // Other scatters (fields pairwise_
          def createScatter(xField: Field, yField: Field): Option[Widget] = {
            val clusterClassField = Field("cluster_class", Some("Cluster Class"), FieldTypeId.Integer, false)

            val widgetSpec = ScatterWidgetSpec(xField.name, yField.name, Some(clusterClassField.name), None, displayOptions)

            // generate a scatter widget
            widgetService.genFromFullData(widgetSpec, jsonsWithClasses, Seq(xField, yField, clusterClassField)).head
          }

          // Transform Scatters into JSONs
          val scatters = numericFields.combinations(2).take(64).flatMap { fields => createScatter(fields(0), fields(1)) }

          val scatterJsons = (Seq(pca12Scatter) ++ scatters).map( scatter =>
            scatterToJson(scatter.asInstanceOf[ScatterWidget[_, _]])
          )

          val classSizeJsons = idClasses.groupBy(_._2).toSeq.sortBy(_._1).map { case (_, values) => JsNumber(values.size) }

          // Independence Test
          val numValues = idClassMap.values.map ( classId => (classId.toString, classId.toString)).toMap
          val clusterClassField = Field("cluster_class", Some("Cluster Class"), FieldTypeId.Enum, false, Some(numValues))
          val fieldNameLabelMap = fields.map(field => (field.name, field.labelOrElseName)).toMap

          val testResults = statsService.testIndependenceSorted(jsonsWithClasses, fields, clusterClassField)

          implicit val stringTestTupleFormat = TupleFormat[String, IndependenceTestResult]

          // Create JSON results
          val jsonResults = testResults.flatMap { case (field, result) =>
            val fieldLabel = fieldNameLabelMap.get(field.name).get

            result.map( result =>
              Json.toJson((fieldLabel, result))
            )
          }

          Ok(Json.obj(
            "classSizes" -> JsArray(classSizeJsons),
            "scatters" -> JsArray(scatterJsons),
            "testResults" -> JsArray(jsonResults)
          ))

        case None =>
          BadRequest(s"ML unsupervised learning model with id ${mlModelId.stringify} not found.")
      }
  }

  override def getIndependenceTest(
    filterOrId: FilterOrId
  ) = AuthAction { implicit request => {
    val dataSetNameTreeSettingFuture = getDataSetNameTreeAndSetting(request)
    val filterFuture = filterRepo.resolve(filterOrId)

    for {
      // get the data set name, the data space tree, and the setting
      (dataSetName, dataSpaceTree, setting) <- dataSetNameTreeSettingFuture

      // use a given filter conditions or load one
      resolvedFilter <- filterFuture

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
    } yield {
      // get a new filter
      val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

      render {
        case Accepts.Html() => Ok(dataset.independenceTest(
          dataSetName,
          newFilter,
          setting.filterShowFieldStyle,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("GetIndependenceTest function doesn't support JSON response.")
      }
    }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the GetIndependenceTest method")
        InternalServerError(t.getMessage)
    }
  }

  override def testIndependence(
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val (inputFieldNames, targetFieldName) = request.body.asFormUrlEncoded.map { inputs =>
      val x = inputs.get("inputFieldNames[]").getOrElse(Nil)
      val y = inputs.get("targetFieldName").map(_.head)
      (x, y)
    }.getOrElse((Nil, None))

    if (inputFieldNames.isEmpty || targetFieldName.isEmpty)
      Future(BadRequest("No input provided."))
    else
      testIndependenceAux(targetFieldName.get, inputFieldNames, filterOrId)
  }

  private def testIndependenceAux(
    targetFieldName: String,
    inputFieldNames: Seq[String],
    filterOrId: FilterOrId
  ): Future[Result] = {
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(targetFieldName)).toSet.toSeq
      else
        Nil

    for {
      // use a given filter conditions or load one
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // criteria
      criteria <- toCriteria(resolvedFilter.conditions)

      // add a target-field-undefined criterion if needed
      finalCriteria = if (independenceTestKeepUndefined) criteria else criteria ++ Seq(NotEqualsNullCriterion(targetFieldName))

      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, finalCriteria)
     } yield {
      val fieldNameLabelMap = fields.map(field => (field.name, field.labelOrElseName)).toMap

      val inputFields = fields.filterNot(_.name.equals(targetFieldName))
      val outputField = fields.find(_.name.equals(targetFieldName)).get

      val resultsSorted = statsService.testIndependenceSorted(jsons, inputFields, outputField, independenceTestKeepUndefined)

      implicit val stringTestTupleFormat = TupleFormat[String, IndependenceTestResult]

      // create jsons
      val labelResults = resultsSorted.flatMap { case (field, result) =>
        val fieldLabel = fieldNameLabelMap.get(field.name).get
        result.map((fieldLabel, _))
      }
      Ok(Json.toJson(labelResults))
    }
  }

  private def loadCriteria(filterId: Option[BSONObjectID]) =
    for {
      filter <- filterId match {
        case Some(filterId) => filterRepo.get(filterId)
        case None => Future(None)
      }

      criteria <- filter match {
        case Some(filter) => toCriteria(filter.conditions)
        case None => Future(Nil)
      }
    } yield
      criteria

  // TODO: this is ugly... fix by introducing a proper JSON formatter
  private def scatterToJson(scatter: ScatterWidget[_, _]): JsValue = {
    def toJson(point: Any) =
      point match {
        case x: Double => JsNumber(x)
        case x: Long => JsNumber(x)
        case x: ju.Date => JsNumber(x.getTime)
        case _ => JsString(point.toString)
      }

    val seriesJsons = scatter.data.map { case (name, data) =>
      Json.obj(
        "name" -> shorten(name),
        "data" -> JsArray(
          data.toSeq.map { case (point1, point2) =>
            JsArray(Seq(toJson(point1), toJson(point2)))
          }
        )
      )
    }

    Json.obj("title" -> scatter.title, "xAxisCaption" -> scatter.xAxisCaption, "yAxisCaption" -> scatter.yAxisCaption, "data" -> JsArray(seriesJsons))
  }

  @Deprecated
  override def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = Action.async { implicit request =>
    for {
      fields <- fieldRepo.find(
        criteria = fieldTypeIds match {
          case Nil => Nil
          case _ => Seq("fieldType" #-> fieldTypeIds)
        },
//        sort = Seq(AscSort("name")),
        projection = Seq("name", "label", "fieldType")
      )
    } yield {
      implicit val fieldFormat = DataSetFormattersAndIds.fieldFormat
      Ok(Json.toJson(fields))
    }
  }

  private implicit val optionFormat = new OptionFormat[String]
  private implicit val tupleFormat = TupleFormat[String, Option[String]]

  override def getFieldNamesAndLabels(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = Action.async { implicit request =>
    for {
      fields <- fieldRepo.find(
        criteria = fieldTypeIds match {
          case Nil => Nil
          case _ => Seq("fieldType" #-> fieldTypeIds)
        },
        //        sort = Seq(AscSort("name")),
        projection = Seq("name", "label", "fieldType")
      )
    } yield {
      val fieldNameAndLabels = fields.map { field => (field.name, field.label)}.toSeq
      Ok(Json.toJson(fieldNameAndLabels))
    }
  }

  override def getField(fieldName: String) = Action.async { implicit request =>
    for {
      field <- fieldRepo.get(fieldName)
    } yield {
      implicit val fieldFormat = DataSetFormattersAndIds.fieldFormat
      Ok(Json.toJson(field))
    }
  }

  override def getFieldTypeWithAllowedValues(fieldName: String) = Action.async { implicit request =>
    for {
      field <- fieldRepo.get(fieldName)
    } yield
      field match {
        case Some(field) =>
          val allowedValues = getAllowedValues(field)
          implicit val tupleFormat = TupleFormat[String, String]
          val json = Json.obj("fieldType" -> field.fieldType, "isArray" -> field.isArray, "allowedValues" -> allowedValues)
          Ok(json)

        case None => BadRequest(s"Field $fieldName does not exist.")
      }
  }

  private def getAllowedValues(field: Field): Traversable[(String, String)] =
    field.fieldType match {
      case FieldTypeId.Enum => field.numValues.map(_.toSeq.sortBy(_._1)).getOrElse(Nil)
      case FieldTypeId.Boolean => Seq(
        "true" -> field.displayTrueValue.getOrElse("true"),
        "false" -> field.displayFalseValue.getOrElse("false")
      )
      case _ => Nil
    }

  override def getFieldValue(id: BSONObjectID, fieldName: String) = Action.async { implicit request =>
//    for {
//      items <- repo.find(criteria = Seq("_id" #== id), projection = Seq(fieldName))
//    } yield
//      items.headOption match {
//        case Some(item) => Ok((item \ fieldName).get)
//        case None => BadRequest(s"Item '${id.stringify}' not found.")
//      }
    for {
      item <- repo.get(id)
    } yield
      item match {
        case Some(item) => Ok((item \ fieldName).get)
        case None => BadRequest(s"Item '${id.stringify}' not found.")
      }
  }

  override def getSeriesProcessingSpec = AuthAction { implicit request =>
    for {
      // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
    } yield
      Ok(dataset.processSeries(dataSetName, seriesProcessingSpecForm, setting.filterShowFieldStyle, tree))
  }

  override def runSeriesProcessing = AuthAction { implicit request =>
    seriesProcessingSpecForm.bindFromRequest.fold(
      { formWithErrors =>
        for {
          // get the data set name, data space tree and the data set setting
          (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
        } yield {
          BadRequest(dataset.processSeries(dataSetName, formWithErrors, setting.filterShowFieldStyle, tree))
        }
      },
      spec =>
        for {
          _ <- dataSetService.processSeriesAndSaveDataSet(spec)
        } yield {
          Redirect(router.getSeriesProcessingSpec).flashing("success" -> s"Series processing finished.")
        }
    )
  }

  override def getSeriesTransformationSpec = AuthAction { implicit request =>
    for {
    // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
    } yield
      Ok(dataset.transformSeries(dataSetName, seriesTransformationSpecForm, setting.filterShowFieldStyle, tree))
  }

  override def runSeriesTransformation = AuthAction { implicit request =>
    seriesTransformationSpecForm.bindFromRequest.fold(
      { formWithErrors =>
        for {
        // get the data set name, data space tree and the data set setting
          (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
        } yield {
          BadRequest(dataset.transformSeries(dataSetName, formWithErrors, setting.filterShowFieldStyle, tree))
        }
      },
      spec =>
        for {
          _ <- dataSetService.transformSeriesAndSaveDataSet(spec)
        } yield {
          Redirect(router.getSeriesTransformationSpec).flashing("success" -> s"Series transformation finished.")
        }
    )
  }

  private def getFieldLabelMap(fieldNames : Traversable[String]): Future[Map[String, String]] = {
    val futureFieldLabelPairs : Traversable[Future[Option[(String, String)]]]=
      fieldNames.map { fieldName =>
        fieldRepo.get(fieldName).map { fieldOption =>
          fieldOption.flatMap{_.label}.map{ label => (fieldName, label)}
        }
      }

    Future.sequence(futureFieldLabelPairs).map{ _.flatten.toMap }
  }

  private def filterSubCriteriaMap(widgetSpecs: Traversable[WidgetSpec])  =
    Future.sequence(
      widgetSpecs.map(_.subFilterId).flatten.toSet.map { subFilterId: BSONObjectID =>

        filterRepo.resolve(Right(subFilterId)).flatMap(resolvedFilter =>
          toCriteria(resolvedFilter.conditions).map(criteria =>
            (subFilterId, criteria)
         )
        )
      }
    )

  //////////////////////
  // Export Functions //
  //////////////////////


  /**
    * Generate and content of TRANSMART data file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  def exportTranSMARTDataFile(delimiter : String) = AuthAction { implicit request =>
    for {
      setting <- dsa.setting

      fileContent <- generateTranSMARTDataFile(
        tranSMARTDataFileName,
        delimiter,
        setting.exportOrderByFieldName
      )
    } yield
      stringToFile(tranSMARTDataFileName)(fileContent)
  }

  /**
    * Generate content of TRANSMART mapping file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  def exportTranSMARTMappingFile(delimiter : String) = AuthAction { implicit request =>
    for {
      setting <- dsa.setting

      fileContent <- generateTranSMARTMappingFile(
        tranSMARTDataFileName,
        delimiter,
        setting.exportOrderByFieldName
      )
    } yield
      stringToFile(tranSMARTMappingFileName)(fileContent)
  }

  /**
    * Generate  content of TRANSMART data file for download.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return VString with file content.
    */
  protected def generateTranSMARTDataFile(
    dataFilename: String,
    delimiter: String,
    orderBy: Option[String]
  ): Future[String] = {
    for {
      setting <- dsa.setting

      records <- repo.find(sort = orderBy.fold(Seq[Sort]())(toSort))

      categories <- categoryRepo.find()

      categoryMap <- fieldNameCategoryMap(categories)

      fields <- fieldRepo.find()
    } yield {
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

      tranSMARTService.createClinicalDataFile(unescapedDelimiter, csvEOL, setting.tranSMARTReplacements)(
        records,
        setting.keyFieldName,
        setting.tranSMARTVisitFieldName,
        categoryMap,
        fields.map(field => (field.name, ftf(field.fieldTypeSpec))).toMap
      )
    }
  }

  /**
    * Generate the content of TRANSMART mapping file for downnload.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return VString with file content.
    */
  protected def generateTranSMARTMappingFile(
    dataFilename: String,
    delimiter: String,
    orderBy: Option[String]
  ): Future[String] = {
    for {
      setting <- dsa.setting

      categories <- categoryRepo.find()

      categoryMap <- fieldNameCategoryMap(categories)

      fieldMap <- fieldLabelMap
    } yield {
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

      tranSMARTService.createMappingFile(unescapedDelimiter, csvEOL, setting.tranSMARTReplacements)(
        dataFilename,
        setting.keyFieldName,
        setting.tranSMARTVisitFieldName,
        categoryMap,
        rootCategoryTree(categories),
        fieldMap
      )
    }
  }

  protected def fieldNameCategoryMap(
    categories: Traversable[Category]
  ): Future[Map[String, Category]] = {
    val idCategories = categories.map{ category =>
      (category._id.get, category)
    }

    for {
      fieldsWithCategories <- fieldRepo.find(
        criteria = Seq("categoryId" #!= None),
        projection = Seq("name", "categoryId", "fieldType")
      )
    } yield {
      val idCategoriesMap = idCategories.toMap
      fieldsWithCategories.map(field =>
        (field.name, idCategoriesMap(field.categoryId.get))
      ).toMap
    }
  }

  protected def fieldLabelMap: Future[Map[String, Option[String]]] =
    for {
      fields <- fieldRepo.find(projection = Seq("name", "label", "fieldType"))
    } yield
      fields.map( field => (field.name, field.label)).toMap

  protected def rootCategoryTree(
    categories: Traversable[Category]
  ): Category = {
    val idCategoryMap = categories.map( category => (category._id.get, category)).toMap
    categories.foreach {category =>
      if (category.parentId.isDefined) {
        val parent = idCategoryMap(category.parentId.get)
        parent.addChild(category)
      }
    }

    val layerOneCategories = categories.filter(_.parentId.isEmpty).toSeq

    new Category("").setChildren(layerOneCategories)
  }
}

case class DataSetShowViewDataHolder(
  id: BSONObjectID,
  title: String,
  item: JsObject,
  listCall: Call,
  nonNullOnly: Boolean,
  fieldNameLabelAndRendererMap: Map[String, (String, JsReadable => String)],
  dataSpaceMetaInfos: Traversable[DataSpaceMetaInfo]
)

object IndependenceTestResult {
  implicit val independenceTestResultFormat: Format[IndependenceTestResult] = new SubTypeFormat[IndependenceTestResult] (
    Seq(
      ManifestedFormat(Json.format[ChiSquareResult]),
      ManifestedFormat(Json.format[OneWayAnovaResult])
    )
  )
}