package controllers.dataset

import java.util.UUID
import java.{util => ju}
import javax.inject.Inject

import scala.collection.mutable.{Map => MMap}
import _root_.util.{FieldUtil, GroupMapList}
import dataaccess.JsonUtil._
import models.ConditionType._
import util.{BasicStats, fieldLabel}
import _root_.util.WebExportUtil._
import _root_.util.{seqFutures, shorten, toHumanReadableCamel}
import dataaccess._
import dataaccess.FilterRepoExtra._
import models.{MultiChartDisplayOptions, _}
import com.google.inject.assistedinject.Assisted
import controllers._
import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import Criterion.Infix
import controllers.core.{ExportableAction, ReadonlyControllerImpl, WebContext}
import org.apache.commons.lang3.StringEscapeUtils
import models.FilterCondition.FilterOrId
import models.Widget.WidgetWrites
import models.ml._
import persistence.RepoTypes.{ClassificationRepo, RegressionRepo, UnsupervisedLearningRepo}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.data.{Form, Mapping}
import play.api.data.Forms.{mapping, number, of, optional}
import play.api.libs.json._
import play.api.mvc.Results._
import play.api.data.Forms.{mapping, _}
import reactivemongo.bson.BSONObjectID
import services._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Filter => _, _}
import reactivemongo.play.json.BSONFormats._
import services.ml.{MachineLearningService, Performance}
import views.html.dataset
import models.ml.DataSetTransformation._
import services.ml.ChiSquareResult.chiSquareResultFormat

import scala.math.Ordering.Implicits._
import scala.concurrent.{Future, TimeoutException}

trait GenericDataSetControllerFactory {
  def apply(dataSetId: String): DataSetController
}

protected[controllers] class DataSetControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    dataSetService: DataSetService,
    widgetService: WidgetGenerationService,
    classificationRepo: ClassificationRepo,
    regressionRepo: RegressionRepo,
    unsupervisedLearningRepo: UnsupervisedLearningRepo,
    dataSpaceService: DataSpaceService,
    tranSMARTService: TranSMARTService
  ) extends ReadonlyControllerImpl[JsObject, BSONObjectID]

    with DataSetController
    with ExportableAction[JsObject] {

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

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

  private implicit val storageTypeFormatter =  EnumFormatter(StorageType)
  private implicit val seriesProcessingSpec = JsonFormatter[SeriesProcessingSpec]
  private implicit val seriesTransformationSpec = JsonFormatter[SeriesTransformationSpec]

  private val coreMapping: Mapping[DataSetTransformationCore] = mapping(
    "resultDataSetId" -> nonEmptyText,
    "resultDataSetName" -> nonEmptyText,
    "resultStorageType" -> of[StorageType.Value],
    "processingBatchSize" -> optional(number(min = 1, max = 200)),
    "saveBatchSize" -> optional(number(min = 1, max = 200))
  )(DataSetTransformationCore.apply)(DataSetTransformationCore.unapply)

  private val seriesProcessingSpecForm = Form(
    mapping(
      "sourceDataSetId" -> ignored(dataSetId),
      "core" -> coreMapping,
      "seriesProcessingSpecs" -> seq(of[SeriesProcessingSpec]),
      "preserveFieldNames" -> seq(nonEmptyText)
    )(DataSetSeriesProcessingSpec.apply)(DataSetSeriesProcessingSpec.unapply))

  private val seriesTransformationSpecForm = Form(
    mapping(
      "sourceDataSetId" -> ignored(dataSetId),
      "core" -> coreMapping,
      "seriesTransformationSpecs" -> seq(of[SeriesTransformationSpec]),
      "preserveFieldNames" -> seq(nonEmptyText)
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

  /**
    * Table displaying given paginated content. Generally used to display fields of the datasets.
    *
    * @param page    Page object containing info (number of pages, current page, ...) for pagination. Contains JsObject represenation of data for display.
    * @param context Web context: request header, messages, and flash
    * @return View for all available fields.
    */
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
  ) = Action.async { implicit request =>
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
        (columnNames, widgetSpecs) = dataView.map(view => (view.tableColumnNames, view.widgetSpecs)).getOrElse((Nil, Nil))

        // widget field Map
        widgetFieldMap <-
          getFields(widgetSpecs.map(_.fieldNames).flatten.toSet).map {
            _.map(field => (field.name, field)).toMap
          }

        // get the response data
        viewResponses <- {
          val useChartRepoMethod = dataView.map(_.useOptimizedRepoChartCalcMethod).getOrElse(false)

          Future.sequence(
            filterOrIdsToUse.zip(tablePagesToUse).map { case (filterOrId, tablePage) =>
              getViewResponseWoWidgets(tablePage.page, tablePage.orderBy, filterOrId, columnNames, widgetFieldMap)
            }
          )
        }
      } yield {
        Logger.info(s"Data loading of a view for the data set '${dataSetId}' finished in ${new ju.Date().getTime - start.getTime} ms")
        val renderingStart = new ju.Date()
        render {
          case Accepts.Html() => {
            val callbackId = UUID.randomUUID.toString

            val useChartRepoMethod = dataView.map(_.useOptimizedRepoChartCalcMethod).getOrElse(false)

            val viewPartsWidgetFutures = (viewResponses, tablePagesToUse, filterOrIdsToUse).zipped.map {
              case (viewResponse, tablePage, filterOrId) =>
                val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * pageLimit, viewResponse.count, tablePage.orderBy, Some(viewResponse.filter))
                val viewData = DataSetViewData(newPage, viewResponse.tableFields)
                val widgetsFuture = getViewWidgets(filterOrId, widgetSpecs, widgetFieldMap, useChartRepoMethod)

                (viewData, widgetsFuture)
            }

            val viewParts = viewPartsWidgetFutures.map(_._1)
            val jsonsFuture = Future.sequence(viewPartsWidgetFutures.map(_._2)).map { allWidgets =>
              val minMaxWidgets = if (allWidgets.size > 1) setBoxPlotMinMax(allWidgets) else allWidgets
              minMaxWidgets.map( widgets =>
                widgetsToJsons(widgets.toSeq, widgetSpecs, widgetFieldMap)
              )
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

  private def widgetsToJsons(
    widgets: Seq[Option[Widget]],
    widgetSpecs: Seq[WidgetSpec],
    widgetFieldMap: Map[String, Field]
  ): JsArray = {
    val jsons = widgetSpecs.zip(widgets).map { case (widgetSpec, widget) =>
      val fields = widgetSpec.fieldNames.map(widgetFieldMap.get).flatten
      val fieldTypes = fields.map(field => ftf(field.fieldTypeSpec).asValueOf[Any])
      widget.map { widget =>
        // fixing integer->double field type for a numerical count widget
        val fieldTypesToUse =
          widget match {
            case e: NumericalCountWidget[Any] =>
              val fieldType = fieldTypes.head
              val fieldTypeToUse =
                if (fieldType.spec.fieldType == FieldTypeId.Integer) {
                  val doubleFieldTypeSpec = fieldType.spec.copy(fieldType = FieldTypeId.Double)
                  ftf(doubleFieldTypeSpec).asInstanceOf[FieldType[Any]]
                } else {
                  fieldType
                }
              Seq(fieldTypeToUse) ++ fieldTypes.tail

            case _ => fieldTypes
        }
        // make a scalar type out of it
        val scalarFieldTypesToUse = fieldTypesToUse.map(fieldType => ftf(fieldType.spec.copy(isArray = false)).asInstanceOf[FieldType[Any]])
        implicit val writes = new WidgetWrites[Any](scalarFieldTypesToUse.toSeq)
        Json.toJson(widget)
      }
    }.flatten
    JsArray(jsons)
  }

  override def getWidgets(
    callbackId: String
  ) = Action.async { implicit request =>
    jsonWidgetResponseCache.get(callbackId).map { jsonWidgets =>
      jsonWidgetResponseCache.remove(callbackId)
      jsonWidgets.map(jsons => Ok(JsArray(jsons)))
    }.getOrElse(
      Future(NotFound(s"Widget response callback $callbackId not found."))
    )
  }

  override def getWidgetPanelAndTable(
    dataViewId: BSONObjectID,
    tablePage: Int,
    tableOrder: String,
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val start = new ju.Date()

    val dataViewFuture = dataViewRepo.get(dataViewId)

    {
      for {
        // load the view
        dataView <- dataViewFuture

        // get the response data
        viewResponse <- {
          val (columnNames, widgetSpecs) =
            dataView.map(view => (view.tableColumnNames, view.widgetSpecs)).getOrElse((Nil, Nil))

          val useChartRepoMethod = dataView.map(_.useOptimizedRepoChartCalcMethod).getOrElse(false)

          getViewResponse(tablePage, tableOrder, filterOrId, columnNames, widgetSpecs, Map(), useChartRepoMethod)
        }
      } yield {
        Logger.info(s"Data loading of a widget panel and a table for the data set '${dataSetId}' finished in ${new ju.Date().getTime - start.getTime} ms")

        val renderingStart = new ju.Date()

        render {
          case Accepts.Html() => {
            val newPage = Page(viewResponse.tableItems, tablePage, tablePage * pageLimit, viewResponse.count, tableOrder, Some(viewResponse.filter))

            val widgetPanel = dataset.filterWidgetPanel("filter", viewResponse.widgets.flatten, dataView.map(_.elementGridWidth).getOrElse(3))

            val table = dataset.viewTable(newPage, viewResponse.tableFields, router, true)

            Logger.info(s"Rendering of a widget panel and a table for the data set '${dataSetId}' finished in ${new ju.Date().getTime - renderingStart.getTime} ms")
            Ok(widgetPanel)
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
      val boxWidgets: Seq[BoxWidget[T]] = widgetsSeqs.map { widgets =>
        widgets(index).collect { case x: BoxWidget[T] => x }
      }.flatten

      boxWidgets match {
        case Nil => None
        case _ =>
          implicit val ordering = boxWidgets.head.ordering
          val minLowerWhisker = boxWidgets.map(_.data.lowerWhisker).min
          val maxUpperWhisker = boxWidgets.map(_.data.upperWhisker).max
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
      widgets.zip(indexMinMaxWhiskers).map { case (widget, (index, minMaxWhiskers)) =>
        minMaxWhiskers match {
          case Some(minMaxWhiskers) =>
            widget.map { widget =>
              widget match {
                case x: BoxWidget[_] =>
                  implicit val ordering = x.ordering
                  setMinMax(x, minMaxWhiskers)
                case _ => widget
              }
            }
          case None => widget
        }
      }
    }
  }

  private def getViewResponse(
    page: Int,
    orderBy: String,
    filterOrId: FilterOrId,
    tableFieldNames: Seq[String],
    widgetSpecs: Seq[WidgetSpec],
    widgetFieldMap: Map[String, Field],
    useOptimizedRepoChartCalcMethod: Boolean
  ): Future[ViewResponse] =
    for {
      // use a given filter conditions or load one
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // get the conditions
      conditions = resolvedFilter.conditions

      // generate criteria
      criteria <- toCriteria(conditions)

      // get the widgets' sub criteria
      filterSubCriteria <- Future.sequence(
        widgetSpecs.map(_.subFilterId).flatten.toSet.map { subFilterId: BSONObjectID =>

          filterRepo.resolve(Right(subFilterId)).flatMap(resolvedFilter =>
            toCriteria(resolvedFilter.conditions).map(criteria =>
              (subFilterId, criteria)
            )
          )
        }
      )

      // create a name -> field map of all the referenced fields for a quick lookup
      nameFieldMap <- {
        val filterFieldNames = conditions.map(_.fieldName.trim).toSet

        if (widgetFieldMap.nonEmpty) {
          val requiredFieldNames =
            (filterFieldNames ++ tableFieldNames).diff(widgetFieldMap.keySet)

          getFields(requiredFieldNames).map { fields =>
            val newMap = fields.map(field => (field.name, field)).toMap
            newMap ++ widgetFieldMap
          }
        } else {
          val widgetFieldNames = widgetSpecs.map(_.fieldNames).flatten.toSet
          val requiredFieldNames = widgetFieldNames ++ tableFieldNames ++ filterFieldNames
          getFields(requiredFieldNames).map {
            _.map(field => (field.name, field)).toMap
          }
        }
      }

      response <- getViewResponseAux(
        page, orderBy, resolvedFilter, criteria, filterSubCriteria.toMap, nameFieldMap, tableFieldNames, widgetSpecs, useOptimizedRepoChartCalcMethod
      )
    } yield
      response

  private def getViewWidgets(
    filterOrId: FilterOrId,
    widgetSpecs: Seq[WidgetSpec],
    widgetFieldMap: Map[String, Field],
    useOptimizedRepoWidgetCalcMethod: Boolean
  ): Future[Traversable[Option[Widget]]] =
    for {
      // use a given filter conditions or load one
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // get the conditions
      conditions = resolvedFilter.conditions

      // generate criteria
      criteria <- toCriteria(conditions)

      // get the widgets' sub criteria
      filterSubCriteria <- Future.sequence(
        widgetSpecs.map(_.subFilterId).flatten.toSet.map { subFilterId: BSONObjectID =>

          filterRepo.resolve(Right(subFilterId)).flatMap(resolvedFilter =>
            toCriteria(resolvedFilter.conditions).map(criteria =>
              (subFilterId, criteria)
            )
          )
        }
      )

      // create a name -> field map of all the referenced fields for a quick lookup
      fields <- {
        val filterFieldNames = conditions.map(_.fieldName.trim).toSet

        if (widgetFieldMap.nonEmpty) {
          val requiredFieldNames =
            filterFieldNames.diff(widgetFieldMap.keySet)

          getFields(requiredFieldNames).map { fields =>
//            val newMap = fields.map(field => (field.name, field)).toMap
            fields ++ widgetFieldMap.values
          }
        } else {
          val widgetFieldNames = widgetSpecs.map(_.fieldNames).flatten.toSet
          val requiredFieldNames = widgetFieldNames ++ filterFieldNames
          getFields(requiredFieldNames)
        }
      }

      widgetFields <- widgetService(widgetSpecs, repo, criteria, filterSubCriteria.toMap, fields, useOptimizedRepoWidgetCalcMethod)
    } yield
      widgetFields.map(_.map(_._1))

  private def getViewResponseWoWidgets(
    page: Int,
    orderBy: String,
    filterOrId: FilterOrId,
    tableFieldNames: Seq[String],
    widgetFieldMap: Map[String, Field]
  ): Future[InitViewResponse] =
    for {
      // use a given filter conditions or load one
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // get the conditions
      conditions = resolvedFilter.conditions

      // generate criteria
      criteria <- toCriteria(conditions)

      // create a name -> field map of all the referenced fields for a quick lookup
      nameFieldMap <- {
        val filterFieldNames = conditions.map(_.fieldName.trim).toSet

        if (widgetFieldMap.nonEmpty) {
          val requiredFieldNames =
            (filterFieldNames ++ tableFieldNames).diff(widgetFieldMap.keySet)

          getFields(requiredFieldNames).map { fields =>
            val newMap = fields.map(field => (field.name, field)).toMap
            newMap ++ widgetFieldMap
          }
        } else {
          val requiredFieldNames = filterFieldNames ++ tableFieldNames
          getFields(requiredFieldNames).map {
            _.map(field => (field.name, field)).toMap
          }
        }
      }

      response <- getViewResponseWoWidgetsAux(page, orderBy, resolvedFilter, criteria, nameFieldMap, tableFieldNames)
    } yield
      response

  private def getViewResponseAux(
    page: Int,
    orderBy: String,
    filter: Filter,
    criteria: Seq[Criterion[Any]],
    widgetSubCriteria: Map[BSONObjectID, Seq[Criterion[Any]]],
    nameFieldMap: Map[String, Field],
    tableFieldNames: Seq[String],
    widgetSpecs: Seq[WidgetSpec],
    useOptimizedRepoChartCalcMethod: Boolean
  ): Future[ViewResponse] = {

    // total count
    val countFuture = repo.count(criteria)

    // widgets
    val widgetsFuture = widgetService(widgetSpecs, repo, criteria, widgetSubCriteria, nameFieldMap.values, useOptimizedRepoChartCalcMethod)

    // table items
    val tableItemsFuture = getTableItems(page, orderBy, criteria, nameFieldMap, tableFieldNames)

    for {
      // obtain the total item count satisfying the resolved filter
      count <- countFuture

      // generate the widgets
      widgetsFields <- widgetsFuture

      // load the table items
      tableItems <- tableItemsFuture
    } yield {
      val tableFields = tableFieldNames.map(nameFieldMap.get).flatten
      val widgets = widgetsFields.map(_.map(_._1))
      val newFilter = setFilterLabels(filter, nameFieldMap)
      ViewResponse(count, widgets, tableItems, newFilter, tableFields)
    }
  }

  private def getViewResponseWoWidgetsAux(
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
  ) = Action.async { implicit request =>
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
    implicit request: Request[_]
  ): Future[Result] = {
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)

    // auxiliary function to retrieve a field definition
    def getField(fieldName: Option[String]): Future[Option[Field]] =
      fieldName.map(fieldRepo.get).getOrElse(Future(None))

    val xFieldFuture = getField(xFieldName)
    val yFieldFuture = getField(yFieldName)
    val groupFieldFuture = getField(groupFieldName)

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
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // get the criteria
      criteria <- toCriteria(resolvedFilter.conditions)

      // collect all the fields
      fields = Seq(xField, yField, groupField).flatten

      xyzItems <-
        (xField zip yField).headOption.map { case (xField, yField) =>
          repo.find(criteria, Nil, fields.map(_.name).toSet)
        }.getOrElse(
          Future(Nil)
        )

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
    } yield {
      val widget: Option[Widget] =
        (xField zip yField).headOption.flatMap { case (xField, yField) =>
          val displayOptions = BasicDisplayOptions(height = Some(500))
          val widgetSpec = ScatterWidgetSpec(xField.name, yField.name, groupField.map(_.name), None, displayOptions)

          widgetService(widgetSpec, xyzItems, fields)
        }

      def mean(field: Field): Option[Double] = {
        field.fieldTypeSpec.fieldType match {
          case FieldTypeId.Integer => {
            val values = getValues[Long](field, xyzItems).flatten
            BasicStats.mean(values.toSeq)
          }
          case FieldTypeId.Double => {
            val values = getValues[Double](field, xyzItems).flatten
            BasicStats.mean(values.toSeq)
          }
          case FieldTypeId.Date => {
            val values = getValues[ju.Date](field, xyzItems).flatten
            BasicStats.mean(values.map(_.getTime).toSeq)
          }
          case _ =>
            None
        }
      }

      val xMean = xField.map(mean).flatten
      val yMean = yField.map(mean).flatten

      val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

      render {
        case Accepts.Html() => Ok(dataset.scatterStats(
          dataSetName,
          xField,
          yField,
          groupField,
          widget,
          xMean,
          yMean,
          newFilter,
          setting.filterShowFieldStyle,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("GetScatterStats function doesn't support JSON response.")
      }
    }
  }

  override def getDistribution(
    fieldNameOption: Option[String],
    groupFieldNameOption: Option[String],
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>

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

        fieldName = fieldNameOption.getOrElse(setting.defaultDistributionFieldName)

        // chart field
        field <- fieldRepo.get(fieldName)

        // get the group field
        groupField <- groupFieldName.map(fieldRepo.get).getOrElse(Future(None))

        // generate widgets - distribution and box
        widgets <- field match {
          case Some(field) =>
            val widgetSpecs: Seq[WidgetSpec] = Seq(
              DistributionWidgetSpec(
                fieldName = field.name,
                groupFieldName = groupField.map(_.name),
                displayOptions = MultiChartDisplayOptions(height = Some(500))
              ),
              BoxWidgetSpec(
                fieldName = field.name,
                displayOptions = BasicDisplayOptions(height = Some(500))
              )
            )
            widgetService(widgetSpecs, repo, criteria, Map(), Seq(field) ++ groupField, false)
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
            widgets.flatMap(_.map(_._1)).toSeq,
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

  def getCorrelations(
    fieldNames: Seq[String],
    filterOrId: FilterOrId
  ) = Action.async { implicit request => {
    for {
    // get the setting
      setting <- dsa.setting

      // get the data set name
      dataSetName <- dsa.dataSetName

      // get the data space tree
      dataSpaceTree <- dataSpaceService.getTreeForCurrentUser(request)

      // use a given filter conditions or load one
      resolvedFilter <- filterRepo.resolve(filterOrId)

      // get the criteria
      criteria <- toCriteria(resolvedFilter.conditions)

      // get the chart items
      chartItems <- if (fieldNames.nonEmpty)
        repo.find(criteria, Nil, fieldNames)
      else
        Future(Nil)

      // chart fields
      fields <- getFields(fieldNames)

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)

    } yield {
      // generate the correlation widget
      val displayOptions = BasicDisplayOptions(height = Some(500))
      val widgetSpec = CorrelationWidgetSpec(fieldNames, None, displayOptions)
      val widget = widgetService(widgetSpec, chartItems, fields)

      // get a new fileter
      val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

      render {
        case Accepts.Html() => Ok(dataset.correlation(
          dataSetName,
          fields,
          widget,
          newFilter,
          setting.filterShowFieldStyle,
          dataSpaceTree
        ))
        case Accepts.Json() => BadRequest("GetDistribution function doesn't support JSON response.")
      }
    }
  }.recover {
    case t: TimeoutException =>
      Logger.error("Problem found in the getCorrelations method")
      InternalServerError(t.getMessage)
  }
  }

  override def getCumulativeCount(
    fieldNameOption: Option[String],
    groupFieldNameOption: Option[String],
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
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
        widget <-
          field.map { field =>
            val projection = Seq(field.name) ++ groupField.map(_.name)
            repo.find(criteria, Nil, projection.toSet).map { items =>

              val widgetSpec = CumulativeCountWidgetSpec(
                fieldName = field.name,
                groupFieldName = groupFieldName,
                displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Line), height = Some(500))
              )

              widgetService(widgetSpec, items, Seq(field) ++ groupField)
            }
          }.getOrElse(
            Future(None)
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
            widget,
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
  ) = Action.async { implicit request => {
      for {
      // get the data set name, data space tree and the data set setting
        (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)

        fieldName = fieldNameOption.getOrElse(setting.defaultDistributionFieldName)

        // field
        field <- fieldRepo.get(fieldName)
      } yield {
        render {
          case Accepts.Html() => Ok(dataset.fractalis(
            dataSetName,
            field,
            setting.filterShowFieldStyle,
            tree
          ))
          case Accepts.Json() => BadRequest("getFractalis function doesn't support JSON response.")
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the getFractalis process")
        InternalServerError(t.getMessage)
    }
  }

  override def getRegression = Action.async { implicit request => {
    for {
    // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
    } yield {
      render {
        case Accepts.Html() => Ok(dataset.regression(
          dataSetName,
          setting.filterShowFieldStyle,
          tree
        ))
        case Accepts.Json() => BadRequest("getRegression function doesn't support JSON response.")
      }
    }
  }.recover {
    case t: TimeoutException =>
      Logger.error("Problem found in the getRegression process")
      InternalServerError(t.getMessage)
  }
  }

  override def getClusterization = Action.async { implicit request => {
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

  override def getIndependenceTest = Action.async { implicit request =>
    {
      for {
      // get the data set name, data space tree and the data set setting
        (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
      } yield {
        render {
          case Accepts.Html() => Ok(dataset.independenceTest(
            dataSetName,
            setting.filterShowFieldStyle,
            tree
          ))
          case Accepts.Json() => BadRequest("getIndependenceTest function doesn't support JSON response.")
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the getIndependenceTest process")
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

  override def regress(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int],
    trainingTestingSplit: Option[Double],
    repetitions: Option[Int],
    crossValidationFolds: Option[Int]
  ) = Action.async { implicit request =>
    val learningSetting = LearningSetting(featuresNormalizationType, pcaDims, trainingTestingSplit, Nil, repetitions, crossValidationFolds)

    for {
      result <- runMLAux(
        regressionRepo.get(mlModelId),
        mlService.regress)(
        inputFieldNames, outputFieldName, filterId, learningSetting
      )
    } yield
      result match {
        case Some(result) =>
          logger.info("Regression finished with the following results: " + Json.stringify(result))
          Ok(result)
        case None =>
          BadRequest(s"ML regression model with id ${mlModelId.stringify} not found.")
      }
  }

  private def runMLAux[M](
    getModel: => Future[Option[M]],
    runML: (Traversable[JsObject], Seq[(String, FieldTypeSpec)], String, M, LearningSetting) => Future[Traversable[Performance]])(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    learningSetting: LearningSetting
  ): Future[Option[JsArray]] = {
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq
      else
        Nil

    val criteriaFuture = loadCriteria(filterId)

    for {
      mlModel <- getModel

      criteria <- criteriaFuture

      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, criteria)

      evalRates <- mlModel.map { mlModel =>
        val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
        runML(jsons, fieldNameAndSpecs, outputFieldName, mlModel, learningSetting)
      }.getOrElse(
        Future(Nil)
      )
    } yield
      mlModel.map { mlModel =>
        val evalJsons = evalRates.toSeq.sortBy(_.evalMetric.id).map { performance =>
          val results = performance.trainingTestResults
            Json.obj(
              "metricName" -> toHumanReadableCamel(performance.evalMetric.toString),
              "trainEvalRate" -> results.map(_._1).sum / results.size, // mean
              "testEvalRate" -> results.map(_._2).sum / results.size   // mean
            )
        }

        JsArray(evalJsons)
      }
  }

  override def cluster(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID],
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDims: Option[Int]
  ) = Action.async { implicit request =>
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

          val numericTypes = Set(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date)
          val numericFields = fields.filter(field => !field.fieldTypeSpec.isArray && numericTypes.contains(field.fieldType))

          val xField = numericFields.headOption
          val yField = numericFields.tail.headOption

          val idClassMap = idClasses.toMap

          // PCA 1-2 Scatter
          val displayOptions = BasicDisplayOptions(height = Some(500))

          val classPCA12s = idPCA12s.map { case (id, pca12) =>
            (idClassMap.get(id).get, pca12)
          }.toGroupMap.toSeq

          val pca12WidgetData = classPCA12s.map { case (clazz, values) =>
            (clazz.toString, "rgba(223, 83, 83, .5)", values.toSeq)
          }

          val pca12Scatter = ScatterWidget("PCA", "PCA 1", "PCA 2", pca12WidgetData, displayOptions)

          // Other scatters (fields pairwise_
          def createScatter(xField: Field, yField: Field): Option[Widget] = {
            val jsonsWithClasses = jsonsWithStringIds.flatMap { json =>
              val stringId = (json \ JsObjectIdentity.name).as[String]
              idClassMap.get(stringId).map(clazz =>  json.+("cluster_class", JsNumber(clazz)))
            }

            val clusterClassField = Field("cluster_class", Some("Cluster Class"), FieldTypeId.Integer, false)

            val widgetSpec = ScatterWidgetSpec(xField.name, yField.name, Some(clusterClassField.name), None, displayOptions)

            // generate a scatter widget
            widgetService(widgetSpec, jsonsWithClasses, Seq(xField, yField, clusterClassField))
          }

          // Transform Scatters into JSONs
          val scatters = numericFields.combinations(2).take(32).flatMap { fields => createScatter(fields(0), fields(1)) }

          val scatterJsons = (Seq(pca12Scatter) ++ scatters).map( scatter =>
            scatterToJson(scatter.asInstanceOf[ScatterWidget[_, _]])
          )

          Ok(JsArray(scatterJsons))

        case None =>
          BadRequest(s"ML unsupervised learning model with id ${mlModelId.stringify} not found.")
      }
  }

  override def testIndependence(
    targetFieldName: String,
    inputFieldNames: Seq[String],
    filterId: Option[BSONObjectID]
  ) = Action.async { implicit request =>
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(targetFieldName)).toSet.toSeq
      else
        Nil

    for {
      criteria <- loadCriteria(filterId)

      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, criteria)
    } yield {
      val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
      val fieldNameLabelMap = fields.map(field => (field.name, field.labelOrElseName)).toMap
      val results = mlService.independenceTest(jsons, fieldNameAndSpecs, targetFieldName)
      val resultJsons = results.map { case (fieldName, result) =>
        Json.obj("fieldLabel" -> fieldNameLabelMap.get(fieldName).get, "result" -> Json.toJson(result))
      }
      Ok(JsArray(resultJsons))
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

    val seriesJsons = scatter.data.map { case (name, color, data) =>
      Json.obj(
        "name" -> shorten(name),
        "data" -> JsArray(
          data.map { case (point1, point2) =>
            JsArray(Seq(toJson(point1), toJson(point2)))
          }
        )
      )
    }

    Json.obj("title" -> scatter.title, "xAxisCaption" -> scatter.xAxisCaption, "yAxisCaption" -> scatter.yAxisCaption, "data" -> JsArray(seriesJsons))
  }

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

  override def getField(fieldName: String) = Action.async { implicit request =>
    for {
      field <- fieldRepo.get(fieldName)
    } yield {
      implicit val fieldFormat = DataSetFormattersAndIds.fieldFormat
      Ok(Json.toJson(field))
    }
  }

  override def getFieldNames = Action.async { implicit request =>
    for {
      fields <- fieldRepo.find(sort = Seq(AscSort("name")))
    } yield {
      val fieldNames = fields.map(_.name)
      Ok(Json.toJson(fieldNames))
    }
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

  override def getSeriesProcessingSpec = Action.async { implicit request =>
    for {
      // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
    } yield
      Ok(dataset.processSeries(dataSetName, seriesProcessingSpecForm, setting.filterShowFieldStyle, tree))
  }

  override def runSeriesProcessing = Action.async { implicit request =>
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

  override def getSeriesTransformationSpec = Action.async { implicit request =>
    for {
    // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
    } yield
      Ok(dataset.transformSeries(dataSetName, seriesTransformationSpecForm, setting.filterShowFieldStyle, tree))
  }

  override def runSeriesTransformation = Action.async { implicit request =>
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

  //////////////////////
  // Export Functions //
  //////////////////////


  /**
    * Generate and content of TRANSMART data file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  def exportTranSMARTDataFile(delimiter : String) = Action.async { implicit request =>
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
  def exportTranSMARTMappingFile(delimiter : String) = Action.async { implicit request =>
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