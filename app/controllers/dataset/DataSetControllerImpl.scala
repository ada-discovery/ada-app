package controllers.dataset

import java.util.concurrent.TimeoutException
import java.{util => ju}
import javax.inject.Inject

import _root_.util.JsonUtil._
import models.ConditionType._
import util.{BasicStats, JsonUtil, fieldLabel}
import _root_.util.WebExportUtil._
import _root_.util.shorten
import dataaccess._
import models.{MultiChartDisplayOptions, _}
import com.google.inject.assistedinject.Assisted
import controllers.{DataSetWebContext, ExportableAction, ReadonlyControllerImpl}
import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import Criterion.Infix
import org.apache.commons.lang3.StringEscapeUtils
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory, DataSpaceMetaInfoRepo}
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.Results._
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import reactivemongo.bson.BSONObjectID
import services.{StatsService, TranSMARTService}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.play.json.BSONFormats._
import views.html.dataset

import reflect.runtime.universe._
import scala.math.Ordering.Implicits._
import scala.collection.generic.SeqFactory
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}

trait GenericDataSetControllerFactory {
  def apply(dataSetId: String): DataSetController
}

protected[controllers] class DataSetControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends ReadonlyControllerImpl[JsObject, BSONObjectID] with DataSetController with ExportableAction[JsObject] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get

  protected val fieldRepo = dsa.fieldRepo
  protected val categoryRepo = dsa.categoryRepo
  protected val filterRepo = dsa.filterRepo
  protected val dataViewRepo = dsa.dataViewRepo

  // not that the associated data set repo could be updated (by calling updateDataSetRepo)
  // therefore it should not be stored as val
  override protected def repo = dsa.dataSetRepo

  @Inject protected var tranSMARTService: TranSMARTService = _
  @Inject protected var statsService: StatsService = _

  // hooks

  protected def dataSetName = result(dsa.metaInfo).name

  // auto-generated filename for csv files
  protected def csvFileName: String = dataSetId.replace(" ", "-") + ".csv"

  // auto-generated filename for json files
  protected def jsonFileName: String = dataSetId.replace(" ", "-") + ".json"

  // auto-generated filename for tranSMART data files
  protected def tranSMARTDataFileName: String = dataSetId.replace(" ", "-") + "_data_file"

  // auto-generated filename for tranSMART mapping files
  protected def tranSMARTMappingFileName: String = dataSetId.replace(" ", "-") + "_mapping_file"

  // setting of data set ui aspects such as overview chart field names, etc.
  protected def setting = result(dsa.setting)

  // router for requests; to be passed to views as helper.
  protected val router = new DataSetRouter(dataSetId)

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  private val ftf = FieldTypeHelper.fieldTypeFactory

  private implicit def toWebContext(implicit request: Request[_]) = {
    implicit val msg = messagesApi.preferred(request)
    DataSetWebContext(dataSetId)
  }

  override protected def listViewColumns = result(
    dataViewRepo.find().map {
    _.filter(_.default).headOption.map(_.tableColumnNames)
    }
  )

  /**
    * Table displaying given paginated content. Generally used to display fields of the datasets.
    *
    * @param page Page object containing info (number of pages, current page, ...) for pagination. Contains JsObject represenation of data for display.
    * @param msg Internal request message.
    * @param request Header of original request.
    * @return View for all available fields.
    */
  override protected def listView(
    page: Page[JsObject])(
    implicit msg: Messages, request: Request[_]
  ) =
    dataset.list(
      dataSetName + " Item",
      page,
      result(getFieldLabelMap(listViewColumns.get)), // TODO: refactor
      listViewColumns.get
    )

  private def getViewView(
    dataViewId: BSONObjectID,
    dataViewName: String,
    viewParts: Seq[DataSetViewData],
    elementGridWidth: Int
  )(implicit request: Request[_]) = {
    val tree = result(dataSpaceTree)
    dataset.showView(
      dataSetName,
      dataViewId,
      dataViewName,
      viewParts,
      elementGridWidth,
      setting.filterShowFieldStyle,
      tree
    )
  }

  /**
    * Shows all fields of the selected subject.
    *
    * @param id BSON ID key of subject.
    * @param item JsObject represenation of subject data.
    * @param msg Internal request message.
    * @param request Header of original request.
    * @return View for subject entries.
    */
  override protected def showView(
    id : BSONObjectID,
    item : JsObject
  )(implicit msg: Messages, request: Request[_]) = {
    val fieldNameLabelAndRendererMapFuture = fieldRepo.find().map(_.map
      { field =>
        val fieldType = ftf(field.fieldTypeSpec)
        (field.name, (field.labelOrElseName, fieldType.jsonToDisplayString(_)))
      }.toMap
    )

    dataset.show(
      dataSetName + " Item",
      item,
      router.getDefaultView,
      true,
      result(fieldNameLabelAndRendererMapFuture),
      result(dataSpaceTree)
    )
  }

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
      setting.exportOrderByFieldName,
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
      setting.exportOrderByFieldName,
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
    fieldRepo.find().map{ fields =>
      if (fields.isEmpty)
        throw new IllegalStateException(s"Empty dictionary found. Pls. create one by running 'runnables.InferXXXDictionary' script.")

      val fieldTypeCounts = ArrayBuffer.fill(FieldTypeId.values.size)(0)
      fields.foreach { field =>
        fieldTypeCounts(field.fieldType.id) += 1
      }

      implicit val msg = messagesApi.preferred(request)
      render {
        case Accepts.Html() => Ok(dataset.typeOverview(dataSetName, (FieldTypeId.values, fieldTypeCounts).zipped.toList))
        case Accepts.Json() => Ok(JsObject(
          (FieldTypeId.values, fieldTypeCounts).zipped.map{ case (fieldType, count) =>
            (fieldType.toString, JsNumber(count))
          }.toSeq
        ))
      }
    }
  }

  private case class ViewResponse(
    count: Int,
    fieldChartSpecs: Traversable[Option[FieldChartSpec]],
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
      selectedView <- dataViewRepo.find().map( views =>
        views.find(_.default) match {
          case Some(view) => Some(view)
          case None => views.headOption
        }
      )
    } yield {
      val webContext = implicitly[DataSetWebContext]

      selectedView match {
        case Some(view) => Redirect(router.getView(view._id.get, Nil, Nil, false))
        case None => Redirect(webContext.dataViewRouter.plainList).flashing("errors" -> "No view to show. You must first define one.")
      }
    }
  }

  override def getView(
    dataViewId: BSONObjectID,
    tablePages: Seq[TablePage],
    filterOrIds: Seq[FilterOrId],
    filterChanged: Boolean
  ) = Action.async { implicit request =>
    val start = new ju.Date()

    {
      for {
        // load the view
        dataView <- dataViewRepo.get(dataViewId)

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
          val padding = Seq.fill(Math.max(filterOrIdsToUse.size - tablePages.size, 0))(TablePage(0, ""))
          tablePages ++ padding
        }

        // get the response data
        viewResponses <- {
          val (columnNames, widgetSpecs) =
            dataView.map( view =>
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
        val end = new ju.Date()

        Logger.info(s"Loading of view for the data set '${dataSetId}' finished in ${end.getTime - start.getTime} ms")
        render {
          case Accepts.Html() => {
            val fieldChartsSpecs =
              if (viewResponses.size > 1)
                setBoxPlotMinMax(viewResponses.map(_.fieldChartSpecs))
              else
                viewResponses.map(_.fieldChartSpecs)

            val viewParts = (viewResponses, fieldChartsSpecs, tablePagesToUse).zipped.map {
              case (viewResponse, fieldChartSpecs, tablePage) =>
                val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * pageLimit, viewResponse.count, tablePage.orderBy, Some(viewResponse.filter))
                DataSetViewData(newPage, fieldChartSpecs.flatten, viewResponse.tableFields)
              }
            Ok(getViewView(
              dataViewId,
              dataView.map(_.name).getOrElse("N/A"),
              viewParts,
              dataView.map(_.elementGridWidth).getOrElse(3))
            )
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

  private def setBoxPlotMinMax(
    fieldChartSpecs: Seq[Traversable[Option[FieldChartSpec]]]
  ): Seq[Traversable[Option[FieldChartSpec]]] = {
    val chartSpecsSeqs = fieldChartSpecs.map(_.toSeq)
    val chartCount = fieldChartSpecs.head.size

    def getMinMaxWhiskers[T](index: Int): Option[(T, T)] = {
      val boxPlots: Seq[BoxWidget[T]] = chartSpecsSeqs.map { chartSpecs =>
        chartSpecs(index).map(_.chartSpec).collect {
          case x: BoxWidget[T] => x
        }
      }.flatten

      boxPlots match {
        case Nil => None
        case _ =>
          implicit val ordering = boxPlots.head.ordering
          val minLowerWhisker = boxPlots.map(_.data.lowerWhisker).min
          val maxUpperWhisker = boxPlots.map(_.data.upperWhisker).max
          Some(minLowerWhisker.asInstanceOf[T], maxUpperWhisker.asInstanceOf[T])
      }
    }

    def setMinMax[T: Ordering](
                                boxPlot: BoxWidget[T],
                                minMax: (Any, Any)
    ): BoxWidget[T] =
      boxPlot.copy(min = Some(minMax._1.asInstanceOf[T]), max = Some(minMax._2.asInstanceOf[T]))

    val indexMinMaxWhiskers =
      for (index <- 0 until chartCount) yield
        (index, getMinMaxWhiskers[Any](index))

    chartSpecsSeqs.map { chartSpecs =>
      chartSpecs.zip(indexMinMaxWhiskers).map{ case (fieldChartSpec, (index, minMaxWhiskers)) =>
        minMaxWhiskers match {
          case Some(minMaxWhiskers) =>
            fieldChartSpec.map { fieldChartSpec =>
              val newChartSpec =
                fieldChartSpec.chartSpec match {
                  case x: BoxWidget[_] =>
                    implicit val ordering = x.ordering
                    setMinMax(x, minMaxWhiskers)
                  case _ => fieldChartSpec.chartSpec
                }
              FieldChartSpec(fieldChartSpec.fieldName, newChartSpec)
            }
          case None => fieldChartSpec
        }
      }
    }
  }

  private def getViewResponse(
    page: Int,
    orderBy: String,
    filterOrId: Either[Seq[FilterCondition], BSONObjectID],
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

      // obtain the total item count satisfying the resolved filter
      count <- getFutureCount(conditions)

      // create a name -> field map of all the referenced fields for a quick lookup
      nameFieldMap <- {
        val chartFieldNames = widgetSpecs.map(_.fieldNames).flatten.toSet
        val filterFieldNames = conditions.map(_.fieldName.trim)
        val requiredFieldNames: Set[String] = (chartFieldNames ++ tableFieldNames ++ filterFieldNames)

        getFields(requiredFieldNames).map{_.map(field => (field.name, field)).toMap}
      }

      // generate the charts
      chartSpecs <- generateCharts(useOptimizedRepoChartCalcMethod, criteria, widgetSpecs, nameFieldMap)

      // load the table items
      tableItems <- {
        val tableFieldNamesToLoad = tableFieldNames.filterNot { tableFieldName =>
          nameFieldMap.get(tableFieldName).map(field => field.isArray || field.fieldType == FieldTypeId.Json).getOrElse(false)
        }
        if (tableFieldNamesToLoad.nonEmpty)
            getFutureItems(Some(page), orderBy, conditions, tableFieldNamesToLoad ++ Seq(JsObjectIdentity.name), Some(pageLimit))
          else
            Future(Nil)
      }
    } yield {
      val tableFields = tableFieldNames.map(nameFieldMap.get).flatten

      val fieldChartSpecs = chartSpecs.map(_.map { case (chartSpec,  fieldNames) =>
        FieldChartSpec(fieldNames.head, chartSpec)
      })

      val newFilter = setFilterLabels(resolvedFilter, nameFieldMap)

      ViewResponse(count, fieldChartSpecs, tableItems, newFilter, tableFields)
    }

  override def findCustom(
    filterOrId: Either[Seq[FilterCondition], BSONObjectID],
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

  private def generateCharts(
    perChartRepoMethod: Boolean,
    criteria: Seq[Criterion[Any]],
    widgetSpecs: Traversable[WidgetSpec],
    nameFieldMap: Map[String, Field]
  ): Future[Traversable[Option[(Widget, Seq[String])]]] = {
    val splitWidgetSpecs: Traversable[Either[WidgetSpec, WidgetSpec]] =
      if (perChartRepoMethod)
        widgetSpecs.collect {
          case p: DistributionWidgetSpec => Left(p)
          case p: BoxWidgetSpec => Left(p)
          case p: CumulativeCountWidgetSpec => if (p.numericBinCount.isDefined) Left(p) else Right(p)
          case p: ScatterWidgetSpec => Right(p)
          case p: CorrelationWidgetSpec => Right(p)
        }
      else
        widgetSpecs.map(Right(_))

    val repoWidgetSpecs = splitWidgetSpecs.map(_.left.toOption).flatten
    val fullDataWidgetSpecs = splitWidgetSpecs.map(_.right.toOption).flatten
    val fullDataFieldNames = fullDataWidgetSpecs.map(_.fieldNames).flatten.toSet

    println("Loaded chart field names: " + fullDataFieldNames.mkString(", "))

    val repoChartSpecsFuture =
      Future.sequence(
        repoWidgetSpecs.par.map { calcSpec =>
          generateChartFromRepo(criteria, nameFieldMap)(calcSpec).map { chartSpec =>
            (calcSpec, chartSpec)
          }
        }.toList
      )

    val fullDataChartSpecsFuture =
      if (fullDataFieldNames.nonEmpty)
        repo.find(criteria, Nil, fullDataFieldNames).map { chartData =>
          fullDataWidgetSpecs.par.map { calcSpec =>
            val chartSpec = generateChart(chartData, nameFieldMap)(calcSpec)
            (calcSpec, chartSpec)
          }.toList
        }
      else
        Future(Nil)

    for {
      chartSpecs1 <- repoChartSpecsFuture
      chartSpecs2 <- fullDataChartSpecsFuture
    } yield {
      val calcSpecChartMap = (chartSpecs1 ++ chartSpecs2).toMap
      // return charts in the specified order
      widgetSpecs.map(calcSpecChartMap.get).flatten
    }
  }

  private def generateChartFromRepo(
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field])(
    widgetSpec: WidgetSpec
  ): Future[Option[(Widget, Seq[String])]] = {
    val chartSpecFuture: Future[Option[Widget]] = widgetSpec match {

      case DistributionWidgetSpec(fieldName, groupFieldName, binCount, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField =  groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          calcDistributionCounts(criteria, field, groupField, binCount).map { countSeries =>
            val chartTitle = getDistributionCountChartTitle(field, groupField)
            createDistributionChartSpec(countSeries, field, chartTitle, false, true, displayOptions)
          }
        }.getOrElse(
          Future(None)
        )

      case CumulativeCountWidgetSpec(fieldName, groupFieldName, binCount, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField =  groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          calcCumulativeCounts(criteria, field, groupField, binCount).map { cumCountSeries =>
            val chartTitle = getCumulativeCountChartTitle(field, groupField)
            val nonZeroCountSeries = cumCountSeries.filter(_._2.exists(_._2 > 0))
            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
            Some(NumericalCountWidget(chartTitle, field.labelOrElseName, nonZeroCountSeries, initializedDisplayOptions))
          }
        }.getOrElse(
          Future(None)
        )

      case BoxWidgetSpec(fieldName, displayOptions) =>
        val field = nameFieldMap.get(fieldName).get
        for {
          quantiles <- statsService.calcQuantiles(repo, criteria, field)
        } yield
          quantiles.map { quants =>
            implicit val ordering = quants.ordering
            BoxWidget(field.labelOrElseName, field.labelOrElseName, quants, None, None, displayOptions)
          }

      case _ => Future(None)
    }
    chartSpecFuture.map(_.map(chartSpec => (chartSpec, widgetSpec.fieldNames.toSeq)))
  }

  private def generateChart(
    items: Traversable[JsObject],
    nameFieldMap: Map[String, Field])(
    widgetSpec: WidgetSpec
  ): Option[(Widget, Seq[String])] = {
    val chartSpecOption: Option[Widget] = widgetSpec match {

      case DistributionWidgetSpec(fieldName, groupFieldName, binCount, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField =  groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          val countSeries = calcDistributionCounts(items, field, groupField, binCount)
          val chartTitle = getDistributionCountChartTitle(field, groupField)
          createDistributionChartSpec(countSeries, field, chartTitle, false, true, displayOptions)
        }.flatten

      case CumulativeCountWidgetSpec(fieldName, groupFieldName, binCount, displayOptions) => {
        val field = nameFieldMap.get(fieldName).get
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        val series = calcCumulativeCounts(items, field, groupField, binCount)
        val nonZeroCountSeries = series.filter(_._2.exists(_._2 > 0))

        val chartTitle = getCumulativeCountChartTitle(field, groupField)

        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
        val chartSpec = NumericalCountWidget(chartTitle, field.labelOrElseName, nonZeroCountSeries, initializedDisplayOptions)
        Some(chartSpec)
      }

      case BoxWidgetSpec(fieldName, displayOptions) =>
        nameFieldMap.get(fieldName).map { field =>
          statsService.calcQuantiles(items, field).map { quants =>
            implicit val ordering = quants.ordering
            BoxWidget(field.labelOrElseName, field.labelOrElseName, quants, None, None, displayOptions)
          }
        }.flatten

      case ScatterWidgetSpec(xFieldName, yFieldName, groupFieldName, displayOptions) => {
        val xField = nameFieldMap.get(xFieldName).get
        val yField = nameFieldMap.get(yFieldName).get
        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)

        val chartSpec = createScatterChartSpec(
          items,
          xField,
          yField,
          groupFieldName.map(nameFieldMap.get).flatten,
          Some(s"$shortXFieldLabel vs. $shortYFieldLabel"),
          displayOptions
        )
        Some(chartSpec)
      }

      case CorrelationWidgetSpec(fieldNames, displayOptions) => {
        val corrFields = fieldNames.map(nameFieldMap.get).flatten

        val chartSpec = createPearsonCorrelationChartSpec(
          items,
          corrFields,
          displayOptions
        )
        Some(chartSpec)
      }
    }
    chartSpecOption.map(chartSpec => (chartSpec, widgetSpec.fieldNames.toSeq))
  }

  private def resolveFilter(
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
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
    getFields(filter.conditions.map(_.fieldName)).map{
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
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = Action.async { implicit request =>
    val settings = setting

    // initialize the x, y, and group field names
    val xFieldName: Option[String] =
      xFieldNameOption.map(Some(_)).getOrElse(
          settings.defaultScatterXFieldName
      )

    val yFieldName: Option[String] =
      yFieldNameOption.map(Some(_)).getOrElse(
        settings.defaultScatterYFieldName
      )

    val groupFieldName: Option[String] =
      groupFieldNameOption.map { fieldName =>
        val trimmed = fieldName.trim
        if (trimmed.isEmpty) None else Some(trimmed)
    }.flatten

    // auxiliary function to retrieve a field definition
    def getField(fieldName: Option[String]): Future[Option[Field]] =
      fieldName.map(fieldRepo.get).getOrElse(Future(None))

    for {
      xField <- getField(xFieldName)
      yField <- getField(yFieldName)
      groupField <- getField(groupFieldName)

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
            xyzItems,
            xField,
            yField,
            groupField,
            None,
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
          result(dataSpaceTree)
        ))
        case Accepts.Json() => BadRequest("GetScatterStats function doesn't support JSON response.")
      }
    }
  }

  override def getDistribution(
    fieldNameOption: Option[String],
    groupFieldNameOption: Option[String],
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = Action.async { implicit request =>
    val fieldName = fieldNameOption.getOrElse(setting.defaultDistributionFieldName)

    val groupFieldName: Option[String] =
      groupFieldNameOption.map { fieldName =>
        val trimmed = fieldName.trim
        if (trimmed.isEmpty) None else Some(trimmed)
      }.flatten

    {
      for {
        // use a given filter conditions or load one
        resolvedFilter <- resolveFilter(filterOrId)

        // get the criteria
        criteria <- toCriteria(resolvedFilter.conditions)

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
        boxChartSpec <- field.map( field =>
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
          createDistributionChartSpec(distributionCountSeries, field, chartTitle, true, false, MultiChartDisplayOptions(height = Some(500)))
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
            result(dataSpaceTree)
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
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = Action.async { implicit request =>
    {
      for {
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
        correlationChartSpec = createPearsonCorrelationChartSpec(chartItems, chartFields.toSeq, BasicDisplayOptions(height = Some(500)))

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
            result(dataSpaceTree)
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
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    // initialize the field name
    val fieldName: Option[String] =
      fieldNameOption.map(Some(_)).getOrElse(
        setting.defaultCumulativeCountFieldName
      )

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
        field <- getField(fieldName)

        groupField <- getField(groupFieldName)
        // use a given filter conditions or load one
        resolvedFilter <- resolveFilter(filterOrId)

        criteria <- toCriteria(resolvedFilter.conditions)

        // retrieve the jsons/items with or without a group field and collect the cumulative counts
        series <-
          field.map { field =>
            val projection = Seq(field.name) ++ groupField.map(_.name)
            repo.find(criteria, Nil, projection.toSet).map( jsons =>
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
          val nonZeroCountSeries = series.filter(_._2.exists(_._2 > 0))
          NumericalCountWidget(chartTitle, field.labelOrElseName, nonZeroCountSeries, options)
        }

        render {
          case Accepts.Html() => Ok(dataset.cumulativeCount(
            dataSetName,
            field,
            groupField,
            chartSpec,
            newFilter,
            setting.filterShowFieldStyle,
            result(dataSpaceTree)
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

  override def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = Action.async { implicit request =>
    for {
      fields <- fieldRepo.find(
        criteria = fieldTypeIds match {
          case Nil => Nil
          case _ => Seq("fieldType" #-> fieldTypeIds)
        },
        sort = Seq(AscSort("name"))
      )
    } yield {
      implicit val fieldFormat = DataSetFormattersAndIds.fieldFormat
      Ok(Json.toJson(fields))
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

  // TODO: tree should be cached
    private def dataSpaceTree =
      DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)

//  private lazy val dataSpaceTree =
//    DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)

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
    val dataSetSetting = setting
    for {
      categories <- categoryRepo.find()
      categoryMap <- fieldNameCategoryMap(categories)
      fieldMap <- fieldLabelMap
    } yield {
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)

      tranSMARTService.createMappingFile(unescapedDelimiter, csvEOL, dataSetSetting.tranSMARTReplacements)(
        dataFilename,
        dataSetSetting.keyFieldName,
        dataSetSetting.tranSMARTVisitFieldName,
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
    countSeries: Seq[(String, Seq[Count[_]])],
    field: Field,
    chartTitle: String,
    showLabels: Boolean,
    showLegend: Boolean,
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
            CategoricalCountWidget(chartTitle, field.labelOrElseName, showLabels, showLegend, nonZeroCountSeriesSorted, initializedDisplayOptions)
          }

          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date => {
            val nonZeroNumCountSeries = countSeries.filter(_._2.nonEmpty).map { case (seriesName, counts) =>
              (seriesName, counts.map ( count => (count.value, count.count)))
            }

            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
            NumericalCountWidget(chartTitle, field.labelOrElseName, nonZeroNumCountSeries, initializedDisplayOptions)
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
  ): Future[Seq[(String, Seq[Count[_]])]] =
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
  ): Seq[(String, Seq[Count[_]])] =
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
  ): Future[Seq[(String, Seq[(Any, Int)])]] =
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
  ): Seq[(String, Seq[(Any, Int)])] =
    numericBinCount match {
      case Some(_) =>
        val distCountsSeries = calcDistributionCounts(items, field, groupField, numericBinCount)
        toCumCounts(distCountsSeries)

      case None =>
        statsService.calcCumulativeCounts(items, field, groupField)
    }

  // function that converts dist counts to cumulative counts by applying simple running sum
  private def toCumCounts(
    distCountsSeries: Seq[(String, Seq[Count[_]])]
  ) =
    distCountsSeries.map { case (seriesName, distCounts) =>
      val cumCounts = distCounts.scanLeft(0) { case (sum, count) =>
        sum + count.count
      }
      val labeledDistCounts = distCounts.map(_.value).zip(cumCounts)
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
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field],
    title: Option[String] = None,
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
    items: Traversable[JsObject],
    fields: Seq[Field],
    displayOptions: DisplayOptions
  ): HeatmapWidget = {
    val correlations = statsService.calcPearsonCorrelations(items, fields)

    val fieldLabels = fields.map(_.labelOrElseName)
    HeatmapWidget("Correlations", fieldLabels, fieldLabels, correlations, Some(-1), Some(1), displayOptions)
  }
}