package controllers.dataset

import java.util.concurrent.TimeoutException
import java.{util => ju}
import javax.inject.Inject

import _root_.util.JsonUtil._
import models.ConditionType._
import util.{BasicStats, fieldLabel}
import _root_.util.WebExportUtil._
import _root_.util.{seqFutures, shorten, toHumanReadableCamel}
import dataaccess._
import models.{MultiChartDisplayOptions, _}
import com.google.inject.assistedinject.Assisted
import controllers._
import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import Criterion.Infix
import controllers.core.{ReadonlyControllerImpl, WebContext}
import org.apache.commons.lang3.StringEscapeUtils
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import models.FilterCondition.FilterOrId
import models.ml.classification.LogisticRegression
import models.ml.regression.LinearRegression
import persistence.RepoTypes.{ClassificationRepo, RegressionRepo, UnsupervisedLearningRepo}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Results._
import reactivemongo.bson.BSONObjectID
import services.{DataSetService, DataSpaceService, StatsService, TranSMARTService}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Filter => _, _}
import reactivemongo.play.json.BSONFormats._
import services.ml.{ClassificationEvalMetric, MachineLearningService, RegressionEvalMetric}
import views.html.dataset

import scala.math.Ordering.Implicits._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

trait GenericDataSetControllerFactory {
  def apply(dataSetId: String): DataSetController
}

protected[controllers] class DataSetControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    dataSetService: DataSetService,
    classificationRepo: ClassificationRepo,
    regressionRepo: RegressionRepo,
    unsupervisedLearningRepo: UnsupervisedLearningRepo,
    dataSpaceService: DataSpaceService
  ) extends ReadonlyControllerImpl[JsObject, BSONObjectID]

    with DataSetController
    with ExportableAction[JsObject] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected val fieldRepo = dsa.fieldRepo
  protected val categoryRepo = dsa.categoryRepo
  protected val filterRepo = dsa.filterRepo
  protected val dataViewRepo = dsa.dataViewRepo

  private val logger = Logger // (this.getClass())

  // not that the associated data set repo could be updated (by calling updateDataSetRepo)
  // therefore it should not be stored as val
  override protected def repo = dsa.dataSetRepo

  @Inject protected var tranSMARTService: TranSMARTService = _
  @Inject protected var statsService: StatsService = _

  // hooks

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

  private val ftf = FieldTypeHelper.fieldTypeFactory

  private implicit def dataSetWebContext(implicit context: WebContext) = DataSetWebContext(dataSetId)

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
    getFields(fieldNames).map(
      _.map { field =>
        val fieldType = ftf(field.fieldTypeSpec)
        val converter = { text: String => fieldType.valueStringToValue(text) }
        (field.name, converter)
      }.toMap
    )

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

    exportToCsv(
      csvFileName,
      delimiter,
      eolToUse,
      if (replaceEolWithSpace) csvCharReplacements else Nil)(
      result(dsa.setting).exportOrderByFieldName,
      filter,
      if (tableColumnsOnly)
        result(dataViewTableColumnNames(dataViewId))
      else
        Nil
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
  ) =
    exportToJson(
      jsonFileName)(
      result(dsa.setting).exportOrderByFieldName,
      filter,
      if (tableColumnsOnly)
        result(dataViewTableColumnNames(dataViewId))
      else
        Nil
    )

  private def dataViewTableColumnNames(
    dataViewId: BSONObjectID
  ): Future[Seq[String]] =
    dataViewRepo.get(dataViewId).map {
      _ match {
        case Some(dataView) => dataView.tableColumnNames
        case None => Nil
      }
    }

  /**
    *
    * Generates a view showing the field types of the records.
    * Repo with inferred type dictionaries required!
    *
    * @return View with piechart showing field types.
    */
  override def overviewFieldTypes = Action.async { implicit request =>
    fieldRepo.find().map { fields =>
      if (fields.isEmpty)
        throw new IllegalStateException(s"Empty dictionary found. Pls. create one by running 'runnables.InferXXXDictionary' script.")

      val fieldTypeCounts = ArrayBuffer.fill(FieldTypeId.values.size)(0)
      fields.foreach { field =>
        fieldTypeCounts(field.fieldType.id) += 1
      }

      render {
        case Accepts.Html() => Ok(dataset.typeOverview(result(dsa.dataSetName), (FieldTypeId.values, fieldTypeCounts).zipped.toList))
        case Accepts.Json() => Ok(JsObject(
          (FieldTypeId.values, fieldTypeCounts).zipped.map { case (fieldType, count) =>
            (fieldType.toString, JsNumber(count))
          }.toSeq
        ))
      }
    }
  }

  private case class ViewResponse(
    count: Int,
    widgets: Traversable[Option[Widget]],
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

        // get the response data
        viewResponses <- {
          val (columnNames, widgetSpecs) =
            dataView.map(view =>
              (view.tableColumnNames, view.widgetSpecs)
            ).getOrElse((Nil, Nil))

          val useChartRepoMethod = dataView.map(_.useOptimizedRepoChartCalcMethod).getOrElse(false)

          Future.sequence(
            filterOrIdsToUse.zip(tablePagesToUse).map { case (filterOrId, tablePage) =>
              getViewResponse(tablePage.page, tablePage.orderBy, filterOrId, columnNames, widgetSpecs, useChartRepoMethod)
            }
          )
        }
      } yield {
        Logger.info(s"Data loading of a view for the data set '${dataSetId}' finished in ${new ju.Date().getTime - start.getTime} ms")
        val renderingStart = new ju.Date()
        render {
          case Accepts.Html() => {
            val fieldChartsSpecs =
              if (viewResponses.size > 1)
                setBoxPlotMinMax(viewResponses.map(_.widgets))
              else
                viewResponses.map(_.widgets)

            val viewParts = (viewResponses, fieldChartsSpecs, tablePagesToUse).zipped.map {
              case (viewResponse, fieldChartSpecs, tablePage) =>
                val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * pageLimit, viewResponse.count, tablePage.orderBy, Some(viewResponse.filter))
                DataSetViewData(newPage, fieldChartSpecs.flatten, viewResponse.tableFields)
            }

            val response = Ok(
              dataset.showView(
                dataSetName,
                dataViewId,
                dataView.map(_.name).getOrElse("N/A"),
                viewParts,
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
          case Some(refererUrl) => Redirect(refererUrl).flashing("errors" -> s"Filter definition problem: ${e.getMessage}")
          case None => Redirect(router.getDefaultView).flashing("errors" -> s"Filter definition problem: ${e.getMessage}")
        }
      }
    }
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

          getViewResponse(tablePage, tableOrder, filterOrId, columnNames, widgetSpecs, useChartRepoMethod)
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
    val filterFuture = resolveFilter(filterOrId)

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
    useOptimizedRepoChartCalcMethod: Boolean
  ): Future[ViewResponse] =
    for {
      // use a given filter conditions or load one
      resolvedFilter <- resolveFilter(filterOrId)

      // get the conditions
      conditions = resolvedFilter.conditions

      // generate criteria
      criteria <- toCriteria(conditions)

      // get the widgets' sub criteria
      filterSubCriteria <- Future.sequence(
        widgetSpecs.map(_.subFilterId).flatten.toSet.map { subFilterId: BSONObjectID =>

          resolveFilter(Right(subFilterId)).flatMap(resolvedFilter =>
            toCriteria(resolvedFilter.conditions).map(criteria =>
              (subFilterId, criteria)
            )
          )
        }
      )

      // create a name -> field map of all the referenced fields for a quick lookup
      nameFieldMap <- {
        val chartFieldNames = widgetSpecs.map(_.fieldNames).flatten.toSet
        val filterFieldNames = conditions.map(_.fieldName.trim)
        val requiredFieldNames: Set[String] = (chartFieldNames ++ tableFieldNames ++ filterFieldNames)

        getFields(requiredFieldNames).map {
          _.map(field => (field.name, field)).toMap
        }
      }

      response <- getViewResponseAux(
        page, orderBy, resolvedFilter, criteria, filterSubCriteria.toMap, nameFieldMap, tableFieldNames, widgetSpecs, useOptimizedRepoChartCalcMethod
      )
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
    val widgetsFuture = generateWidgets(useOptimizedRepoChartCalcMethod, criteria, widgetSubCriteria, widgetSpecs, nameFieldMap)

    // table items
    val tableItemsFuture = getTableItems(page, orderBy, criteria, nameFieldMap, tableFieldNames)

    for {
    // obtain the total item count satisfying the resolved filter
      count <- countFuture

      // generate the charts
      chartSpecs <- widgetsFuture

      // load the table items
      tableItems <- tableItemsFuture
    } yield {
      val tableFields = tableFieldNames.map(nameFieldMap.get).flatten
      val widgets = chartSpecs.map(_.map(_._1))
      val newFilter = setFilterLabels(filter, nameFieldMap)
      ViewResponse(count, widgets, tableItems, newFilter, tableFields)
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
      resolvedFilter <- resolveFilter(filterOrId)

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

  private def generateWidgets(
    perChartRepoMethod: Boolean,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    widgetSpecs: Traversable[WidgetSpec],
    nameFieldMap: Map[String, Field]
  ): Future[Traversable[Option[(Widget, Seq[String])]]] = {

    // a helper function to get the sub criteria for the widget
    def getSubCriteria(filterId: Option[BSONObjectID]): Seq[Criterion[Any]] =
      filterId.map(subFilterId =>
        widgetFilterSubCriteriaMap.get(subFilterId).getOrElse(Nil)
      ).getOrElse(Nil)

    val splitWidgetSpecs: Traversable[Either[WidgetSpec, WidgetSpec]] =
      if (perChartRepoMethod)
        widgetSpecs.collect {
          case p: DistributionWidgetSpec => Left(p)
          case p: BoxWidgetSpec => Left(p)
          case p: CumulativeCountWidgetSpec => if (p.numericBinCount.isDefined) Left(p) else Right(p)
          case p: ScatterWidgetSpec => Right(p)
          case p: CorrelationWidgetSpec => Right(p)
          case p: BasicStatsWidgetSpec => Right(p)
          case p: TemplateHtmlWidgetSpec => Left(p)
        }
      else
        widgetSpecs.map(Right(_))

    val repoWidgetSpecs = splitWidgetSpecs.map(_.left.toOption).flatten
    val fullDataWidgetSpecs = splitWidgetSpecs.map(_.right.toOption).flatten
    val fullDataFieldNames = fullDataWidgetSpecs.map(_.fieldNames).flatten.toSet

    println("Loaded chart field names: " + fullDataFieldNames.mkString(", "))

    val repoWidgetSpecsFuture =
      Future.sequence(
        repoWidgetSpecs.par.map { widgetSpec =>

          generateChartFromRepo(
            criteria ++ getSubCriteria(widgetSpec.subFilterId),
            nameFieldMap)(
            widgetSpec
          ).map(
            chartSpec =>(widgetSpec, chartSpec)
          )
        }.toList
      )

    val fullDataWidgetSpecsFuture =
      if (fullDataWidgetSpecs.nonEmpty) {
        Future.sequence(
          fullDataWidgetSpecs.groupBy(_.subFilterId).map { case (subFilterId, widgetSpecs) =>
            val fieldNames = widgetSpecs.map(_.fieldNames).flatten.toSet

            repo.find(criteria ++ getSubCriteria(subFilterId), Nil, fieldNames).map { chartData =>
              widgetSpecs.par.map { widgetSpec =>
                val widget = generateChart(chartData, nameFieldMap)(widgetSpec)
                (widgetSpec, widget)
              }.toList
            }
          }
        )
      } else
        Future(Nil)

    for {
      chartSpecs1 <- repoWidgetSpecsFuture
      chartSpecs2 <- fullDataWidgetSpecsFuture
    } yield {
      val specWidgetMap = (chartSpecs1 ++ chartSpecs2.flatten).toMap
      // return widgets in the specified order
      widgetSpecs.map(specWidgetMap.get).flatten
    }
  }

  private def generateChartFromRepo(
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field])(
    widgetSpec: WidgetSpec
  ): Future[Option[(Widget, Seq[String])]] = {
    val title = widgetSpec.displayOptions.title
    val chartSpecFuture: Future[Option[Widget]] = widgetSpec match {

      case DistributionWidgetSpec(fieldName, groupFieldName, subFilter, useRelativeValues, numericBinCount, useDateMonthBins, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          calcDistributionCounts(criteria, field, groupField, numericBinCount).map { countSeries =>
            val chartTitle = title.getOrElse(getDistributionCountChartTitle(field, groupField))

            createDistributionChartSpec(countSeries, field, chartTitle, false, true, useRelativeValues, false, displayOptions)
          }
        }.getOrElse(
          Future(None)
        )

      case CumulativeCountWidgetSpec(fieldName, groupFieldName, subFilter, useRelativeValues, numericBinCount, useDateMonthBins, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          calcCumulativeCounts(criteria, field, groupField, numericBinCount).map { cumCountSeries =>
            val chartTitle = title.getOrElse(getCumulativeCountChartTitle(field, groupField))

            val nonZeroCountSeries = cumCountSeries.filter(_._2.exists(_.count > 0))
            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
            Some(NumericalCountWidget(chartTitle, field.labelOrElseName, useRelativeValues, true, nonZeroCountSeries, initializedDisplayOptions))
          }
        }.getOrElse(
          Future(None)
        )

      case BoxWidgetSpec(fieldName, subFilter, displayOptions) =>
        val field = nameFieldMap.get(fieldName).get
        for {
          quantiles <- statsService.calcQuantiles(repo, criteria, field)
        } yield
          quantiles.map { quants =>
            implicit val ordering = quants.ordering
            val chartTitle = title.getOrElse(field.labelOrElseName)
            BoxWidget(chartTitle, field.labelOrElseName, quants, None, None, displayOptions)
          }

      case TemplateHtmlWidgetSpec(content, subFilter, displayOptions) =>
        val widget = HtmlWidget("", content, displayOptions)
        Future(Some(widget))

      case _ => Future(None)
    }
    chartSpecFuture.map(_.map(chartSpec => (chartSpec, widgetSpec.fieldNames.toSeq)))
  }

  private def generateChart(
    items: Traversable[JsObject],
    nameFieldMap: Map[String, Field])(
    widgetSpec: WidgetSpec
  ): Option[(Widget, Seq[String])] = {
    val title = widgetSpec.displayOptions.title

    val chartSpecOption: Option[Widget] = widgetSpec match {

      case DistributionWidgetSpec(fieldName, groupFieldName, subFilter, useRelativeValues, numericBinCount, useDateMonthBins, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          val countSeries = calcDistributionCounts(items, field, groupField, numericBinCount)
          val chartTitle = title.getOrElse(getDistributionCountChartTitle(field, groupField))
          createDistributionChartSpec(countSeries, field, chartTitle, false, true, useRelativeValues, false, displayOptions)
        }.flatten

      case CumulativeCountWidgetSpec(fieldName, groupFieldName, subFilter, useRelativeValues, numericBinCount, useDateMonthBins, displayOptions) =>
        val field = nameFieldMap.get(fieldName).get
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        val series = calcCumulativeCounts(items, field, groupField, numericBinCount)
        val nonZeroCountSeries = series.filter(_._2.exists(_.count > 0))

        val chartTitle = title.getOrElse(getCumulativeCountChartTitle(field, groupField))

        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
        val chartSpec = NumericalCountWidget(chartTitle, field.labelOrElseName, useRelativeValues, true, nonZeroCountSeries, initializedDisplayOptions)
        Some(chartSpec)

      case BoxWidgetSpec(fieldName, subFilter, displayOptions) =>
        nameFieldMap.get(fieldName).map { field =>
          statsService.calcQuantiles(items, field).map { quants =>
            implicit val ordering = quants.ordering
            val chartTitle = title.getOrElse(field.labelOrElseName)
            BoxWidget(chartTitle, field.labelOrElseName, quants, None, None, displayOptions)
          }
        }.flatten

      case ScatterWidgetSpec(xFieldName, yFieldName, groupFieldName, subFilter, displayOptions) =>
        val xField = nameFieldMap.get(xFieldName).get
        val yField = nameFieldMap.get(yFieldName).get
        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)

        val chartTitle = title.getOrElse(s"$shortXFieldLabel vs. $shortYFieldLabel")

        val chartSpec = createScatterChartSpec(
          Some(chartTitle),
          items,
          xField,
          yField,
          groupFieldName.map(nameFieldMap.get).flatten,
          displayOptions
        )
        Some(chartSpec)

      case CorrelationWidgetSpec(fieldNames, subFilter, displayOptions) =>
        val corrFields = fieldNames.map(nameFieldMap.get).flatten

        val chartTitle = title.getOrElse("Correlations")

        val chartSpec = createPearsonCorrelationChartSpec(
          Some(chartTitle),
          items,
          corrFields,
          displayOptions
        )
        Some(chartSpec)

      case TemplateHtmlWidgetSpec(content, subFilter, displayOptions) =>
        val widget = HtmlWidget("", content, displayOptions)
        Some(widget)
    }

    chartSpecOption.map(chartSpec => (chartSpec, widgetSpec.fieldNames.toSeq))
  }

  private def resolveFilter(
    filterOrId: FilterOrId
  ): Future[Filter] = {
    // use a given filter conditions or load one
    filterOrId match {
      case Right(id) =>
        filterRepo.get(id).map(
          _.getOrElse(new Filter(Nil))
        )
      case Left(filter) => Future(new models.Filter(filter))
    }
  }

  private def setFilterLabels(
    filter: Filter,
    fieldNameMap: Map[String, Field]
  ): Filter = {
    def valueStringToDisplayString[T](
      fieldType: FieldType[T],
      text: String
    ): String = {
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
              value.split(",").map(valueStringToDisplayString(fieldType, _)).mkString(", ")

            case _ => valueStringToDisplayString(fieldType, value)
          }
          condition.copy(fieldLabel = field.label, valueLabel = Some(valueLabel))
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
      resolvedFilter <- resolveFilter(filterOrId)

      criteria <- toCriteria(resolvedFilter.conditions)

      xyzItems <-
      (xField zip yField).headOption.map { case (xField, yField) =>
        val projection = (Seq(xField, yField) ++ groupField).map(_.name)
        repo.find(criteria, Nil, projection.toSet)
      }.getOrElse(
        Future(Nil)
      )

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
    } yield {
      val chartSpec: Option[ScatterWidget] =
        (xField zip yField).headOption.map { case (xField, yField) =>
          createScatterChartSpec(
            None,
            xyzItems,
            xField,
            yField,
            groupField,
            BasicDisplayOptions(height = Some(500))
          )
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
          chartSpec,
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
        resolvedFilter <- resolveFilter(filterOrId)

        // get the criteria
        criteria <- toCriteria(resolvedFilter.conditions)

        // get the data set setting
        setting <- dsa.setting

        fieldName = fieldNameOption.getOrElse(setting.defaultDistributionFieldName)

        // chart field
        field <- fieldRepo.get(fieldName)

        // get the group field
        groupField <- groupFieldName.map(fieldRepo.get).getOrElse(Future(None))

        // calc distribution/counts
        distributionCountSeries <- field match {
          case Some(field) => calcDistributionCounts(criteria, field, groupField, None)
          case None => Future(Nil)
        }

        // generate the box chart (if possible)
        boxChartSpec <- field.map(field =>
          for {
            quantiles <- statsService.calcQuantiles(repo, criteria, field)
          } yield
            quantiles.map { quants =>
              implicit val ordering = quants.ordering
              BoxWidget(field.labelOrElseName, field.labelOrElseName, quants, None, None, BasicDisplayOptions(height = Some(500)))
            }
        ).getOrElse(
          Future(None)
        )

        // create a name -> field map of the filter referenced fields
        fieldNameMap <- getFilterFieldNameMap(resolvedFilter)

      } yield {
        // create a distribution chart and set the height
        val distributionChart = field.map { field =>
          val chartTitle = getDistributionCountChartTitle(field, groupField)
          createDistributionChartSpec(distributionCountSeries, field, chartTitle, true, false, false, false, MultiChartDisplayOptions(height = Some(500)))
        }.flatten

        val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

        render {
          case Accepts.Html() => Ok(dataset.distribution(
            dataSetName,
            field,
            groupField,
            Seq(distributionChart, boxChartSpec).flatten,
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
      resolvedFilter <- resolveFilter(filterOrId)

      // get the criteria
      criteria <- toCriteria(resolvedFilter.conditions)

      // get the chart items
      chartItems <- if (fieldNames.nonEmpty)
        repo.find(criteria, Nil, fieldNames)
      else
        Future(Nil)

      // chart fields
      chartFields <- getFields(fieldNames)

      // generate the correlation chart
      correlationChartSpec = createPearsonCorrelationChartSpec(None, chartItems, chartFields.toSeq, BasicDisplayOptions(height = Some(500)))

      // get the current fields
      currentFields <- fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)

    } yield {
      // set the height of the charts
      val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

      render {
        case Accepts.Html() => Ok(dataset.correlation(
          dataSetName,
          currentFields,
          Some(correlationChartSpec),
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
        resolvedFilter <- resolveFilter(filterOrId)

        criteria <- toCriteria(resolvedFilter.conditions)

        // retrieve the jsons/items with or without a group field and collect the cumulative counts
        series <-
        field.map { field =>
          val projection = Seq(field.name) ++ groupField.map(_.name)
          repo.find(criteria, Nil, projection.toSet).map(jsons =>
            calcCumulativeCounts(jsons, field, groupField, None)
          )
        }.getOrElse(
          Future(Nil)
        )

        // create a name -> field map of the filter referenced fields
        fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
      } yield {
        val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

        val options = MultiChartDisplayOptions(chartType = Some(ChartType.Line), height = Some(500))
        val chartSpec = field.map { field =>
          val chartTitle = getCumulativeCountChartTitle(field, groupField)
          val nonZeroCountSeries = series.filter(_._2.exists(_.count > 0))
          NumericalCountWidget(chartTitle, field.labelOrElseName, false, true, nonZeroCountSeries, options)
        }

        render {
          case Accepts.Html() => Ok(dataset.cumulativeCount(
            dataSetName,
            field,
            groupField,
            chartSpec,
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

    val filterFuture = resolveFilter(filterOrId)

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

  override def getClassification = Action.async { implicit request => {
    for {
    // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
    } yield {
      render {
        case Accepts.Html() => Ok(dataset.classification(
          dataSetName,
          setting.filterShowFieldStyle,
          tree
        ))
        case Accepts.Json() => BadRequest("getClassification function doesn't support JSON response.")
      }
    }
  }.recover {
    case t: TimeoutException =>
      Logger.error("Problem found in the getClassification process")
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

  override def getUnsupervisedLearning = Action.async { implicit request => {
    for {
    // get the data set name, data space tree and the data set setting
      (dataSetName, tree, setting) <- getDataSetNameTreeAndSetting(request)
    } yield {
      render {
        case Accepts.Html() => Ok(dataset.unsupervisedLearning(
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

  override def classify(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq
      else
        Nil

    val mlModelFuture = classificationRepo.get(mlModelId)
    val criteriaFuture = resolveFilter(filterOrId).flatMap(filter => toCriteria(filter.conditions))

    for {
      mlModel <- mlModelFuture
      criteria <- criteriaFuture
      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, criteria)
    } yield
      mlModel match {
        case Some(mlModel) =>
          val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
          val evalRates = mlService.classify(jsons, fieldNameAndSpecs, outputFieldName, mlModel)
          val evalJsons = evalRates.map { case (metric, trainEvalRate, testEvalRate) =>
            Json.obj(
              "metricName" -> toHumanReadableCamel(metric.toString),
              "trainEvalRate" -> trainEvalRate,
              "testEvalRate" -> testEvalRate
            )
          }
          Ok(JsArray(evalJsons.toSeq))
        case None =>
          BadRequest(s"ML classification model with id ${mlModelId.stringify} not found.")
      }
  }

  override def regress(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(outputFieldName)).toSet.toSeq
      else
        Nil

    val mlModelFuture = regressionRepo.get(mlModelId)
    val criteriaFuture = resolveFilter(filterOrId).flatMap(filter => toCriteria(filter.conditions))

    for {
      mlModel <- mlModelFuture
      criteria <- criteriaFuture
      (jsons, fields) <- dataSetService.loadDataAndFields(dsa, explFieldNamesToLoads, criteria)
    } yield
      mlModel match {
        case Some(mlModel) =>
          val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))
          val evalRates = mlService.regress(jsons, fieldNameAndSpecs, outputFieldName, mlModel)
          val evalJsons = evalRates.map { case (metric, trainEvalRate, testEvalRate) =>
            Json.obj(
              "metricName" -> toHumanReadableCamel(metric.toString),
              "trainEvalRate" -> trainEvalRate,
              "testEvalRate" -> testEvalRate
            )
          }
          Ok(JsArray(evalJsons.toSeq))
        case None =>
          BadRequest(s"ML regression model with id ${mlModelId.stringify} not found.")
      }
  }

  override def learnUnsupervised(
    mlModelId: BSONObjectID,
    inputFieldNames: Seq[String],
    filterOrId: FilterOrId
  ) = Action.async { implicit request =>
    val explFieldNamesToLoads =
      if (inputFieldNames.nonEmpty)
        (inputFieldNames ++ Seq(JsObjectIdentity.name)).toSet.toSeq
      else
        Nil

    val mlModelFuture = unsupervisedLearningRepo.get(mlModelId)
    val criteriaFuture = resolveFilter(filterOrId).flatMap(filter => toCriteria(filter.conditions))

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

          val idClasses = mlService.learnUnsupervised(jsonsWithStringIds, fieldNameAndSpecs, mlModel)

          val numericTypes = Set(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Double)
          val numericFields = fields.filter(field => !field.fieldTypeSpec.isArray && numericTypes.contains(field.fieldType))

          val xField = numericFields.headOption
          val yField = numericFields.tail.headOption

          val idClassMap = idClasses.toMap

          def createScatterJson(xField: Field, yField: Field): JsValue = {
            val jsonsWithClasses = jsonsWithStringIds.map { json =>
              val stringId = (json \ JsObjectIdentity.name).as[String]
              idClassMap.get(stringId).map(clazz =>
                json.+("cluster_class", JsNumber(clazz))
              )
            }.flatten

            val clusterClassField = Field("cluster_class", Some("Cluster Class"), FieldTypeId.Integer, false)
            val scatter = createScatterChartSpec(
              None,
              jsonsWithClasses,
              xField,
              yField,
              Some(clusterClassField),
              BasicDisplayOptions(height = Some(500))
            )

            scatterToJson(scatter)
          }

          val scatterJsons = numericFields.combinations(2).take(4).map ( fields =>
            createScatterJson(fields(0), fields(1))
          ).toSeq

          Ok(JsArray(scatterJsons))
        case None =>
          BadRequest(s"ML unsupervised learning model with id ${mlModelId.stringify} not found.")
      }
  }

  // TODO: this is ugly... fix by introducing a proper JSON formatter
  private def scatterToJson(scatter: ScatterWidget): JsValue = {
    val seriesJsons = scatter.data.map { case (name, color, data) =>
      Json.obj(
        "name" -> shorten(name),
        "data" -> JsArray(
          data.map { point =>
            JsArray(point.map(value =>
              value match {
                case x: Double => JsNumber(x)
                case x: Long => JsNumber(x)
                case x: ju.Date => JsNumber(x.getTime)
                case _ => JsString(value.toString)
              }
            ))
          }.toSeq
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

  private def createDistributionChartSpec(
    countSeries: Seq[(String, Seq[Count[Any]])],
    field: Field,
    chartTitle: String,
    showLabels: Boolean,
    showLegend: Boolean,
    useRelativeValues: Boolean,
    isCumulative: Boolean,
    displayOptions: MultiChartDisplayOptions
  ): Option[Widget] = {
    val fieldTypeId = field.fieldTypeSpec.fieldType

    val nonZeroExists = countSeries.exists(_._2.nonEmpty)
    if (nonZeroExists)
      Some(
        fieldTypeId match {
          case FieldTypeId.String | FieldTypeId.Enum | FieldTypeId.Boolean | FieldTypeId.Null | FieldTypeId.Json => {
            // enforce the same categories in all the series
            val labelGroupedCounts = countSeries.map(_._2).flatten.groupBy(_.value)
            val nonZeroLabelSumCounts = labelGroupedCounts.map { case (label, counts) =>
              (label, counts.map(_.count).sum)
            }.filter(_._2 > 0)

            val sortedLabels: Seq[String] = nonZeroLabelSumCounts.toSeq.sortBy(_._2).map(_._1.toString)

            val countSeriesSorted = countSeries.map { case (seriesName, counts) =>

              val labelCountMap = counts.map { count =>
                val label = count.value.toString
                (label, Count(label, count.count, count.key))
              }.toMap

              val newCounts = sortedLabels.map ( label =>
                labelCountMap.get(label).getOrElse(Count(label, 0, None))
              )
              (seriesName, newCounts)
            }
            val nonZeroCountSeriesSorted = countSeriesSorted.filter(_._2.exists(_.count > 0))

            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Pie)))
            CategoricalCountWidget(chartTitle, field.name, field.labelOrElseName, showLabels, showLegend, useRelativeValues, isCumulative, nonZeroCountSeriesSorted, initializedDisplayOptions)
          }

          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date => {
            val nonZeroNumCountSeries = countSeries.filter(_._2.nonEmpty)
            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
            NumericalCountWidget(chartTitle, field.labelOrElseName, useRelativeValues, isCumulative, nonZeroNumCountSeries, initializedDisplayOptions)
          }
        }
      )
    else
      Option.empty[Widget]
  }

  private def calcDistributionCounts(
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Future[Seq[(String, Seq[Count[Any]])]] =
    groupField match {
      case Some(groupField) =>
        statsService.calcGroupedDistributionCounts(repo, criteria, field, groupField, numericBinCount)
      case None =>
        statsService.calcDistributionCounts(repo, criteria, field, numericBinCount).map(counts =>
          Seq(("All", counts))
        )
    }

  private def calcDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Seq[(String, Seq[Count[Any]])] =
    groupField match {
      case Some(groupField) =>
        statsService.calcGroupedDistributionCounts(items, field, groupField, numericBinCount)

      case None =>
        val counts = statsService.calcDistributionCounts(items, field, numericBinCount)
        Seq(("All", counts))
    }

  private def calcCumulativeCounts(
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Future[Seq[(String, Seq[Count[Any]])]] =
    numericBinCount match {
      case Some(_) =>
        calcDistributionCounts(criteria, field, groupField, numericBinCount).map( distCountsSeries =>
          toCumCounts(distCountsSeries)
        )

      case None =>
        val projection = Seq(field.name) ++ groupField.map(_.name)
        repo.find(criteria, Nil, projection.toSet).map( jsons =>
          statsService.calcCumulativeCounts(jsons, field, groupField)
        )
    }

  private def calcCumulativeCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Seq[(String, Seq[Count[Any]])] =
    numericBinCount match {
      case Some(_) =>
        val distCountsSeries = calcDistributionCounts(items, field, groupField, numericBinCount)
        toCumCounts(distCountsSeries)

      case None =>
        statsService.calcCumulativeCounts(items, field, groupField)
    }

  // function that converts dist counts to cumulative counts by applying simple running sum
  private def toCumCounts[T](
    distCountsSeries: Seq[(String, Seq[Count[T]])]
  ): Seq[(String, Seq[Count[T]])] =
    distCountsSeries.map { case (seriesName, distCounts) =>
      val cumCounts = distCounts.scanLeft(0) { case (sum, count) =>
        sum + count.count
      }
      val labeledDistCounts: Seq[Count[T]] = distCounts.map(_.value).zip(cumCounts).map { case (value, count) =>
        Count(value, count, None)
      }
      (seriesName, labeledDistCounts)
    }

  private def getDistributionCountChartTitle(
    field: Field,
    groupField: Option[Field]
  ): String = {
    val label = field.label.getOrElse(fieldLabel(field.name))
    groupField.map { groupField =>
      val groupShortLabel = shorten(groupField.label.getOrElse(fieldLabel(groupField.name)), 25)
      shorten(label, 25) + " by " + groupShortLabel
    }.getOrElse(
      label
    )
  }

  private def getCumulativeCountChartTitle(
    field: Field,
    groupField: Option[Field]
  ): String = {
    val label = field.label.getOrElse(fieldLabel(field.name))
    groupField match {
      case Some(groupField) =>
        val groupShortLabel = shorten(groupField.label.getOrElse(fieldLabel(groupField.name)), 25)
        shorten(label, 25) + " by " + groupShortLabel
      case None => label
    }
  }

  private def createScatterChartSpec(
    title: Option[String],
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field],
    displayOptions: DisplayOptions = BasicDisplayOptions()
  ): ScatterWidget = {
    val data = statsService.collectScatterData(xyzItems, xField, yField, groupField)
    ScatterWidget(
      title.getOrElse("Comparison"),
      xField.labelOrElseName,
      yField.labelOrElseName,
      data.map { case (name, values) =>
        val initName = if (name.isEmpty) "Undefined" else name
        (initName, "rgba(223, 83, 83, .5)", values.map(pair => Seq(pair._1, pair._2)))
      },
      displayOptions
    )
  }

  private def createPearsonCorrelationChartSpec(
    title: Option[String],
    items: Traversable[JsObject],
    fields: Seq[Field],
    displayOptions: DisplayOptions
  ): HeatmapWidget = {
    val correlations = statsService.calcPearsonCorrelations(items, fields)

    val fieldLabels = fields.map(_.labelOrElseName)
    HeatmapWidget(
      title.getOrElse("Correlations"),
      fieldLabels,
      fieldLabels,
      correlations,
      Some(-1),
      Some(1),
      displayOptions
    )
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