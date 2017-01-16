package controllers.dataset

import java.util.concurrent.TimeoutException
import java.{util => ju}
import javax.inject.Inject

import _root_.util.JsonUtil._
import models.ConditionType._
import util.{BasicStats, fieldLabel, JsonUtil}
import _root_.util.WebExportUtil._
import _root_.util.shorten
import dataaccess._
import models._
import com.google.inject.assistedinject.Assisted
import controllers.{ExportableAction, ReadonlyControllerImpl}
import models.DataSetFormattersAndIds.{JsObjectIdentity, FieldIdentity}
import Criterion.Infix
import org.apache.commons.lang3.StringEscapeUtils
import persistence.RepoTypes._
import persistence.dataset.{DataSpaceMetaInfoRepo, DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.Results._
import play.api.mvc.{Action, AnyContent, RequestHeader, Request}
import reactivemongo.bson.BSONObjectID
import services.{ChartService, TranSMARTService}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.play.json.BSONFormats._
import views.html.dataset

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
  @Inject protected var chartService: ChartService = _

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
  protected val jsRouter = new DataSetJsRouter(dataSetId)
  protected val filterRouter = new FilterRouter(dataSetId)
  protected val filterJsRouter = new FilterJsRouter(dataSetId)
  protected val dataViewRouter = new DataViewRouter(dataSetId)
  protected val dataViewJsRouter = new DataViewJsRouter(dataSetId)

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  private val ftf = FieldTypeHelper.fieldTypeFactory

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
  override protected def listView(page: Page[JsObject])(implicit msg: Messages, request: Request[_]) =
    dataset.list(
      dataSetName + " Item",
      page,
      result(getFieldLabelMap(listViewColumns.get)), // TODO: refactor
      listViewColumns.get,
      router
    )

  private def getViewView(
    dataViewId: BSONObjectID,
    page: Page[JsObject],
    fieldChartSpecs: Traversable[FieldChartSpec],
    tableFields: Traversable[Field],
    elementGridWidth: Int
  )(implicit msg: Messages, request: Request[_]) = {
    dataset.showView(
      dataSetName + " Item",
      dataViewId,
      page,
      tableFields,
      fieldChartSpecs,
      elementGridWidth,
      setting.filterShowFieldStyle,
      router,
      jsRouter,
      filterRouter,
      filterJsRouter,
      dataViewRouter,
      dataViewJsRouter,
      result(dataSpaceTree)
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
      router.plainOverviewList,
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
    fieldChartSpecs: Traversable[FieldChartSpec],
    tableItems: Traversable[JsObject],
    filter: Filter,
    tableFields: Traversable[Field]
  )

  override def overviewList(
    page: Int,
    orderBy: String,
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = Action.async { implicit request =>
    Future(Redirect(router.getDefaultView))
  }

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
      selectedView match {
        case Some(view) => Redirect(router.getView(view._id.get, 0, "", Left(Nil), false))
        case None => Redirect(dataViewRouter.plainList).flashing("errors" -> "No view to show. You must first define one.")
      }
    }
  }

  override def getView(
    dataViewId: BSONObjectID,
    page: Int,
    orderBy: String,
    filterOrId: Either[Seq[FilterCondition], BSONObjectID],
    filterChanged: Boolean
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val start = new ju.Date()

    {
      for {
        // load the view
        dataView <- dataViewRepo.get(dataViewId)

        // get the response data
        viewResponse <- {
          val (columnNames, statsCalcSpecs) =
            dataView.map( view =>
              (view.tableColumnNames, view.statsCalcSpecs)
            ).getOrElse((Nil, Nil))

          val viewFilterOrId = dataView.map(_.filterOrIds.headOption).flatten

          val filterOrIdToUse =
            if (!filterChanged && viewFilterOrId.isDefined && filterOrId.isLeft && filterOrId.left.get.isEmpty) {
              viewFilterOrId.get
            } else
              filterOrId

          val useChartRepoMethod = dataView.map(_.useOptimizedRepoChartCalcMethod).getOrElse(false)

          getViewResponse(page, orderBy, filterOrIdToUse, columnNames, statsCalcSpecs, useChartRepoMethod)
        }
      } yield {
        val end = new ju.Date()

        Logger.info(s"Loading of view for the data set '${dataSetId}' finished in ${end.getTime - start.getTime} ms")
        render {
          case Accepts.Html() => {
            val newPage = Page(viewResponse.tableItems, page, page * pageLimit, viewResponse.count, orderBy, Some(viewResponse.filter))
            Ok(getViewView(
              dataViewId,
              newPage,
              viewResponse.fieldChartSpecs,
              viewResponse.tableFields,
              dataView.map(_.elementGridWidth).getOrElse(3))
            )
          }
          case Accepts.Json() => Ok(Json.toJson(viewResponse.tableItems))
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the getView process")
        InternalServerError(t.getMessage)
      case e: AdaConversionException => {
        request.headers.get("Referer") match {
          case Some(refererUrl) => Redirect(refererUrl).flashing("errors" -> s"Filter definition problem: ${e.getMessage}")
          case None => Redirect(router.plainOverviewList).flashing("errors" -> s"Filter definition problem: ${e.getMessage}")
        }
      }
    }
  }

  private def getViewResponse(
    page: Int,
    orderBy: String,
    filterOrId: Either[Seq[FilterCondition], BSONObjectID],
    tableFieldNames: Seq[String],
    statsCalcSpecs: Seq[StatsCalcSpec],
    useOptimizedRepoChartCalcMethod: Boolean
  ): Future[ViewResponse] = {
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
        val chartFieldNames = statsCalcSpecs.map(_.fieldNames).flatten.toSet
        val filterFieldNames = conditions.map(_.fieldName.trim)
        val requiredFieldNames: Set[String] = (chartFieldNames ++ tableFieldNames ++ filterFieldNames)

        getFields(requiredFieldNames).map{_.map(field => (field.name, field)).toMap}
      }

      // generate the charts
      chartSpecs <- generateCharts(useOptimizedRepoChartCalcMethod, criteria, statsCalcSpecs, nameFieldMap)

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

      val fieldChartSpecs = chartSpecs.map { case (chartSpec,  fieldNames) =>
        FieldChartSpec(fieldNames.head, chartSpec)
      }

      val newFilter = setFilterLabels(resolvedFilter, nameFieldMap)

      ViewResponse(count, fieldChartSpecs, tableItems, newFilter, tableFields)
    }
  }

  private def generateCharts(
    perChartRepoMethod: Boolean,
    criteria: Seq[Criterion[Any]],
    statsCalcSpecs: Traversable[StatsCalcSpec],
    nameFieldMap: Map[String, Field]
  ): Future[Traversable[(ChartSpec, Seq[String])]] = {
    val splitStatsCalcSpecs: Traversable[Either[StatsCalcSpec, StatsCalcSpec]] =
      if (perChartRepoMethod)
        statsCalcSpecs.collect {
         case p: DistributionCalcSpec => Left(p)
          case p: BoxCalcSpec => Left(p)
          case p: ScatterCalcSpec => Right(p)
          case p: CorrelationCalcSpec => Right(p)
        }
      else
        statsCalcSpecs.map(Right(_))

    val repoStatsCalcSpecs = splitStatsCalcSpecs.map(_.left.toOption).flatten
    val fullDataStatsCalcSpecs = splitStatsCalcSpecs.map(_.right.toOption).flatten
    val fullDataFieldNames = fullDataStatsCalcSpecs.map(_.fieldNames).flatten.toSet

    println("Loaded chart field names: " + fullDataFieldNames.mkString(", "))

    val repoChartSpecsFuture =
      Future.sequence(
        repoStatsCalcSpecs.par.map { calcSpec =>
          generateChartRepo(criteria, nameFieldMap)(calcSpec).map { chartSpec =>
            (calcSpec, chartSpec)
          }
        }.toList
      )

    val fullDataChartSpecsFuture =
      if (fullDataFieldNames.nonEmpty)
        repo.find(criteria, Nil, fullDataFieldNames).map { chartData =>
          fullDataStatsCalcSpecs.par.map { calcSpec =>
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
      statsCalcSpecs.map(
        calcSpecChartMap.get
      ).flatten.flatten
    }
  }

  private def generateChartRepo(
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field])(
    calcSpec: StatsCalcSpec
  ): Future[Option[(ChartSpec, Seq[String])]] = {
    val outputGridWidth = calcSpec.outputGridWidth
    val chartSpecFuture: Future[Option[ChartSpec]] = calcSpec match {

      case DistributionCalcSpec(fieldName, chartType, _) =>
        nameFieldMap.get(fieldName).map { field =>
          chartService.createDistributionChartSpec(
            repo, criteria, chartType, field, false, true, outputGridWidth)
        }.getOrElse(
          Future(None)
        )

      case BoxCalcSpec(fieldName, _) => {
        chartService.createBoxChartSpecRepo(
          repo, criteria, nameFieldMap.get(fieldName).get, outputGridWidth
        )
      }

      case _ => Future(None)
    }
    chartSpecFuture.map(_.map(chartSpec => (chartSpec, calcSpec.fieldNames.toSeq)))
  }

  private def generateChart(
    items: Traversable[JsObject],
    nameFieldMap: Map[String, Field])(
    calcSpec: StatsCalcSpec
  ): Option[(ChartSpec, Seq[String])] = {
    val outputGridWidth = calcSpec.outputGridWidth
    val chartSpecOption: Option[ChartSpec] = calcSpec match {

      case DistributionCalcSpec(fieldName, chartType, _) =>
        nameFieldMap.get(fieldName).map { field =>
          chartService.createDistributionChartSpec(
            items, chartType, field, false, true, outputGridWidth
          )
        }.flatten

      case BoxCalcSpec(fieldName, _) =>
        nameFieldMap.get(fieldName).map { field =>
          chartService.createBoxChartSpec(
            items, field, outputGridWidth
          )
        }.flatten

      case ScatterCalcSpec(xFieldName, yFieldName, groupFieldName, _) => {
        val xField = nameFieldMap.get(xFieldName).get
        val yField = nameFieldMap.get(yFieldName).get
        val shortXFieldLabel = shorten(xField.labelOrElseName, 15)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 15)

        //          val groupedLabel = groupFieldName.map(_ => "[grouped]").getOrElse("")

        val chartSpec = chartService.createScatterChartSpec(
          items,
          xField,
          yField,
          groupFieldName.map(nameFieldMap.get).flatten,
          Some(s"$shortXFieldLabel vs. $shortYFieldLabel"),
          outputGridWidth
        )
        Some(chartSpec)
      }

      case CorrelationCalcSpec(fieldNames, _) => {
        val corrFields = fieldNames.map(nameFieldMap.get).flatten

        val chartSpec = chartService.createPearsonCorrelationChartSpec(
          items,
          corrFields,
          outputGridWidth
        )
        Some(chartSpec)
      }
    }
    chartSpecOption.map(chartSpec => (chartSpec, calcSpec.fieldNames.toSeq))
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
    implicit val msg = messagesApi.preferred(request)
    val settings = setting

    // initialize x, y, and group field names
    val xFieldName = xFieldNameOption.getOrElse(settings.defaultScatterXFieldName)
    val yFieldName = yFieldNameOption.getOrElse(settings.defaultScatterYFieldName)
    val groupFieldName: Option[String] = groupFieldNameOption match {
      case Some(fieldName) => {
        val trimmed = fieldName.trim
        if (trimmed.isEmpty)
          None
        else
          Some(trimmed)
      }
      case None => None
    }

    val xFieldFuture = fieldRepo.get(xFieldName)
    val yFieldFuture = fieldRepo.get(yFieldName)
    val groupFieldFuture = groupFieldName.map(fieldRepo.get).getOrElse(Future(None))

    for {
      xField <- xFieldFuture
      yField <- yFieldFuture
      groupField <- groupFieldFuture

      // use a given filter conditions or load one
      resolvedFilter <- resolveFilter(filterOrId)

      criteria <- toCriteria(resolvedFilter.conditions)

      xyzItems <- {
        val projection = Seq(xFieldName, yFieldName) ++ groupField.map(_.name)
        repo.find(criteria, Nil, projection.toSet)
      }

      // create a name -> field map of the filter referenced fields
      fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
    } yield {
      val chartSpec: Option[ScatterChartSpec] =
        (xField zip yField).headOption.map { case (xField, yField) =>
          chartService.createScatterChartSpec(
            xyzItems,
            xField,
            yField,
            groupField
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
          chartSpec.map(_.copy(height = Some(500))),
          xMean,
          yMean,
          newFilter,
          setting.filterShowFieldStyle,
          router,
          filterRouter,
          filterJsRouter,
          dataViewRouter,
          dataViewJsRouter,
          dataSetId,
          result(dataSpaceTree)
        ))
        case Accepts.Json() => BadRequest("GetScatterStats function doesn't support JSON response.")
      }
    }
  }

  override def getDistribution(
    fieldNameOption: Option[String],
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val fieldName = fieldNameOption.getOrElse(setting.defaultDistributionFieldName)

    {
      for {
        // use a given filter conditions or load one
        resolvedFilter <- resolveFilter(filterOrId)

        // get the criteria
        criteria <- toCriteria(resolvedFilter.conditions)

        // chart field
        chartField <- fieldRepo.get(fieldName)

        // generate the distribution chart
        distributionChartSpec <- chartField match {
          case Some(field) => chartService.createDistributionChartSpec(repo, criteria, None, field, true, false, None)
          case None => Future(None)
        }

        // generate the box chart (if possible)
        boxChartSpec <- chartField.map( field =>
          chartService.createBoxChartSpecRepo(repo, criteria, field)
        ).getOrElse(
          Future(None)
        )

        // get the current field
        field <- fieldRepo.get(fieldName)

        // create a name -> field map of the filter referenced fields
        fieldNameMap <- getFilterFieldNameMap(resolvedFilter)

      } yield {
        // set the height of the charts
        val distributionChart = distributionChartSpec.map {
          _ match {
            case x: CategoricalChartSpec => x.copy(height = Some(500))
            case x: NumericalChartSpec => x.copy(height = Some(500))
            case x: ChartSpec => x
          }
        }

        val boxChart = boxChartSpec.map(_.copy(height = Some(500)))
        val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)
        render {
          case Accepts.Html() => Ok(dataset.distribution(
            dataSetName,
            field,
            Seq(distributionChart, boxChart).flatten,
            newFilter,
            setting.filterShowFieldStyle,
            router,
            filterRouter,
            filterJsRouter,
            dataViewRouter,
            dataViewJsRouter,
            dataSetId,
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
    implicit val msg = messagesApi.preferred(request)

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
        correlationChartSpec = chartService.createPearsonCorrelationChartSpec(chartItems, chartFields)

        // get the current fields
        currentFields <- fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

        // create a name -> field map of the filter referenced fields
        fieldNameMap <- getFilterFieldNameMap(resolvedFilter)

      } yield {
        // set the height of the charts
        val newChart = correlationChartSpec.copy(height = Some(500))
        val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)
        render {
          case Accepts.Html() => Ok(dataset.correlation(
            dataSetName,
            currentFields,
            Some(newChart),
            newFilter,
            setting.filterShowFieldStyle,
            router,
            filterRouter,
            filterJsRouter,
            dataViewRouter,
            dataViewJsRouter,
            dataSetId,
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

  override def getDateCount(
    dateFieldNameOption: Option[String],
    groupFieldName: Option[String],
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val dateFieldName = dateFieldNameOption.getOrElse(setting.defaultDateCountFieldName)

    val dateFieldFuture = fieldRepo.get(dateFieldName)
    val groupFieldFuture = groupFieldName.map(fieldRepo.get).getOrElse(Future(None))

    {
      for {
        dateField <- dateFieldFuture

        groupField <- groupFieldFuture
        // use a given filter conditions or load one
        resolvedFilter <- resolveFilter(filterOrId)

        series <- dateField match {
          case Some(dateField) => getDateCountData(resolvedFilter.conditions, dateField, groupField)
          case None => Future(Nil)
        }

        // create a name -> field map of the filter referenced fields
        fieldNameMap <- getFilterFieldNameMap(resolvedFilter)
      } yield {
        val newFilter = setFilterLabels(resolvedFilter, fieldNameMap)

        render {
          case Accepts.Html() => Ok(dataset.dateCount(
            dataSetName,
            dateField,
            groupField,
            series,
            newFilter,
            setting.filterShowFieldStyle,
            router,
            filterRouter,
            filterJsRouter,
            dataSetId,
            result(dataSpaceTree)
          ))
          case Accepts.Json() => BadRequest("GetDateCount function doesn't support JSON response.")
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the getDateCountd process")
        InternalServerError(t.getMessage)
    }
  }

  private def getDateCountData(
    filter: Seq[FilterCondition],
    dateField: Field,
    groupField: Option[Field]
  ): Future[Seq[(String, Seq[(ju.Date, Any)])]] = {
    val dateFieldName = dateField.name

    val criteria = result(toCriteria(filter))
    val projection = Seq(dateFieldName) ++ groupField.map(_.name)
    val dateGroupItemsFuture = repo.find(criteria, Nil, projection.toSet)

    val dateFieldType = ftf(dateField.fieldTypeSpec).asInstanceOf[FieldType[ju.Date]]

    val groupDatesFuture: Future[Seq[(String, Seq[ju.Date])]] =
      dateGroupItemsFuture.map { dateGroupItems =>
        val dateGroupSeq = dateGroupItems.toSeq
        val dateJsons = project(dateGroupSeq, dateFieldName).toSeq
        val dates = dateJsons.map(dateFieldType.jsonToValue)

        groupField match {
          case Some(zField) => {
            val groupFieldType = ftf(zField.fieldTypeSpec)
            val groupJsons = project(dateGroupSeq, zField.name).toSeq
            val groups = groupJsons.map(groupFieldType.jsonToDisplayString)

            // TODO: simplify this
            (groups, dates).zipped.map { case (group, date) =>
              (Some(group), date).zipped
            }.flatten.groupBy(_._1).map { case (group, values) =>
              (group, values.map(_._2))
            }.toSeq
          }
          case None =>
            Seq(("all", dates.flatten))
        }
      }

    groupDatesFuture.map(_.map{ case (name, dates) =>
      val count = (dates.sorted, Stream.from(1)).zipped.toSeq
      (name, count)
    })
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
    for {
      items <- repo.find(criteria = Seq("_id" #== id), projection = Seq(fieldName))
    } yield
      items.headOption match {
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

  private def dataSpaceTree =
    DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)

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
      fileContent <- generateTranSMARTDataFile(tranSMARTDataFileName, delimiter, setting.exportOrderByFieldName)
    } yield {
      stringToFile(tranSMARTDataFileName)(fileContent)
    }
  }

  /**
    * Generate content of TRANSMART mapping file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  def exportTranSMARTMappingFile(delimiter : String) = Action.async { implicit request =>
    for {
      fileContent <- generateTranSMARTMappingFile(tranSMARTDataFileName, delimiter, setting.exportOrderByFieldName)
    } yield {
      stringToFile(tranSMARTMappingFileName)(fileContent)
    }
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
}