package controllers.dataset

import java.text.SimpleDateFormat
import java.util.concurrent.TimeoutException
import java.util.Date
import javax.inject.Inject

import _root_.util.JsonUtil._
import _root_.util.{FilterCondition, fieldLabel, JsonUtil}
import _root_.util.WebExportUtil._
import dataaccess._
import models._
import com.google.inject.assistedinject.Assisted
import controllers.{ExportableAction, ReadonlyControllerImpl}
import DataSetFormattersAndIds.FieldIdentity
import Criterion.CriterionInfix
import org.apache.commons.lang3.StringEscapeUtils
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, RequestHeader, Request}
import reactivemongo.bson.BSONObjectID
import services.TranSMARTService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.play.json.BSONFormats._
import views.html.dataset

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}

trait GenericDataSetControllerFactory {
  def apply(dataSetId: String): DataSetController
}

protected[controllers] class DataSetControllerImpl @Inject() (
    @Assisted val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends ReadonlyControllerImpl[JsObject, BSONObjectID](dsaf(dataSetId).get.dataSetRepo) with DataSetController with ExportableAction[JsObject] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get
  protected val fieldRepo = dsa.fieldRepo
  protected val categoryRepo = dsa.categoryRepo

  @Inject protected var tranSMARTService: TranSMARTService = _

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

  private val csvCharReplacements = Map("\n" -> " ", "\r" -> " ")
  private val csvEOL = "\n"

  private val numericTypes = Seq(FieldType.Double.toString, FieldType.Integer.toString)
  private val categoricalTypes = Seq(FieldType.Enum.toString, FieldType.String.toString, FieldType.Boolean.toString)

  override protected def listViewColumns = Some(setting.listViewTableColumnNames)

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

  /**
    * Table displaying given paginated content with charts on the top. Generally used to display fields of the datasets.
    *
    * @param page Page object containing info (number of pages, current page, ...) for pagination. Contains JsObject representation of data for display.
    * @param msg Internal request message.
    * @param request Header of original request.
    * @return View for all available fields.
    */
  private def overviewListView(
    page: Page[JsObject],
    fieldChartSpecs: Traversable[FieldChartSpec],
    tableFields: Traversable[Field]
  )(implicit msg: Messages, request: Request[_]) =
    dataset.overviewList(
      dataSetName + " Item",
      page,
      tableFields,
      listViewColumns.get,
      fieldChartSpecs,
      setting.overviewChartElementGridWidth,
      router,
      jsRouter,
      getMetaInfos
    )

  /**
    * Shows all fields of the selected subject.
    *
    * @param id BSON ID key of subject.
    * @param item JsObject represenation of subject data.
    * @param msg Internal request message.
    * @param request Header of original request.
    * @return View for subject entries.
    */
  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: Request[_]) =
    dataset.show(
      dataSetName + " Item",
      item,
      router.plainOverviewList,
      true,
      getMetaInfos
    )

  /**
    * Generate content of csv export file and create download.
    *
    * @param delimiter Delimiter for csv output file.
    * @return View for download.
    */
  override def exportRecordsAsCsv(
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
      if (tableColumnsOnly) listViewColumns.get else Nil
    )
  }

  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  override def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) =
    exportToJson(
      jsonFileName)(
      setting.exportOrderByFieldName,
      filter,
      if (tableColumnsOnly) listViewColumns.get else Nil
    )

  /**
    * Fetch specified field (column) entries from repo and wrap them in a Traversable object.
    * If a field contains an empty json, it is represented by None.
    *
    * @param fieldName Name of the field of interest.
    * @return Traversable object containing the String entries of the field.
    */
  private def getFieldValues(fieldName : String): Future[Traversable[Option[String]]] =
    for {
      field <- fieldRepo.get(fieldName)
      values <- repo.find(projection = Seq(fieldName))
    } yield {
      values.map { item =>
        val jsValue: JsValue = (item \ fieldName).get
        JsonUtil.toString(jsValue)
      }
    }

  /**
    * TODO: Instead of throwing an exception, provide option to run one of the standalone scripts for dictionary generation.
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

      val fieldTypeCounts = ArrayBuffer.fill(FieldType.values.size)(0)
      fields.foreach { field =>
        fieldTypeCounts(field.fieldType.id) += 1
      }

      implicit val msg = messagesApi.preferred(request)
      render {
        case Accepts.Html() => Ok(dataset.typeOverview(dataSetName, (FieldType.values, fieldTypeCounts).zipped.toList))
        case Accepts.Json() => Ok(JsObject(
          (FieldType.values, fieldTypeCounts).zipped.map{ case (fieldType, count) =>
            (fieldType.toString, JsNumber(count))
          }.toSeq
        ))
      }
    }
  }

  override def overviewList(page: Int, orderBy: String, filter: Seq[FilterCondition]) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val start = new java.util.Date()
    val fieldCharts = setting.overviewFieldChartTypes
    val chartFieldNames = fieldCharts.map(_.fieldName)
    val tableFieldNames = listViewColumns.get

    val requiredFieldNames = (chartFieldNames ++ tableFieldNames).toSet

    val fieldNameMapFuture: Future[Map[String, Field]] =
      getFields(requiredFieldNames).map{_.map(field => (field.name, field)).toMap}

    val chartItemsFuture = repo.find(toCriteria(filter), Nil, chartFieldNames)

    val futureTableCount = getFutureCount(filter)

    {
      for {
        count <- futureTableCount
        fieldNameMap <- fieldNameMapFuture
        items <- {
          val tableFieldNamesToLoad = tableFieldNames.filterNot { tableFieldName =>
            fieldNameMap.get(tableFieldName).map(field => field.isArray || field.fieldType == FieldType.Json).getOrElse(false)
          }
          getFutureItems(Some(page), orderBy, filter, tableFieldNamesToLoad, Some(pageLimit))
        }
        chartItems <- chartItemsFuture
      } yield {
        val tableFieldNameMap = tableFieldNames.map(fieldName =>
          fieldNameMap.get(fieldName).map(field =>
            (fieldName, field)
          )
        ).flatten.toMap
        val tableFields = tableFieldNames.map(fieldNameMap.get).flatten

        val renamedItems = items.map(renameValues(tableFieldNameMap, _))

        val chartFields = chartFieldNames.map(fieldNameMap.get).flatten
        val chartSpecs = createChartSpecs(fieldCharts, chartFields, chartItems)
        val fieldChartSpecs = chartSpecs.map(chartSpec => FieldChartSpec(chartSpec._1, chartSpec._2))

        val end = new java.util.Date()

        Logger.info(s"Loading of '${dataSetId}' finished in ${end.getTime - start.getTime} ms")
        render {
          case Accepts.Html() => Ok(overviewListView(Page(renamedItems, page, page * pageLimit, count, orderBy, filter), fieldChartSpecs, tableFields))
          case Accepts.Json() => Ok(Json.toJson(renamedItems))
        }
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the overviewList process")
        InternalServerError(t.getMessage)
    }
  }

  // TODO just temporary
  private def renameValues(
    fieldNameMap: Map[String, Field],
    item: JsObject
  ): JsObject = {
    val newValueFutures: Traversable[(String, JsValue)] = item.fields.map { case (fieldName, value) =>
      val stringValue = JsonUtil.toString(value)

      val newValue: JsValue = if (stringValue.isDefined) {
        fieldNameMap.get(fieldName).map { field =>
          val enumValues = field.numValues

          if (enumValues.isDefined) {
            enumValues.get.get(stringValue.get).map(JsString(_)).getOrElse(value)
          } else
            value
        }.getOrElse(value)
      } else
       value

      (fieldName, newValue)
    }

    JsObject(newValueFutures.toSeq)
  }

  /**
    * Infers which chart type to use for a field by checking its type.
    * Strings are identified as pie charts.
    * Doubles and Integers are identified as Barplots.
    * All plots which can not be matched are identified as pie charts.
    *
    * @param fieldChartTypes Names of the fields. Used to find fields in data repo.
    * @return Chart specs.
    */
  private def getDataChartSpecs(
    criteria: Seq[Criterion[Any]],
    fieldChartTypes: Traversable[FieldChartType],
    showLabels: Boolean = false,
    showLegend: Boolean = true
  ): Future[Traversable[(String, ChartSpec)]] = {
    val fieldNames = fieldChartTypes.map(_.fieldName)
    val fieldsFuture = getFields(fieldNames)

    val itemsFuture = repo.find(criteria, Nil, fieldNames)

    for {
      foundFields <- fieldsFuture
      items <- itemsFuture
    } yield
      createChartSpecs(fieldChartTypes, foundFields, items, showLabels, showLegend)
  }

  private def createChartSpecs(
    fieldChartTypes: Traversable[FieldChartType],
    fields: Traversable[Field],
    items: Traversable[JsObject],
    showLabels: Boolean = false,
    showLegend: Boolean = true
  ): Traversable[(String, ChartSpec)] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    val createChartSpecAux = createChartSpec(items, showLabels, showLegend)_
    fieldChartTypes.map { case FieldChartType(fieldName, chartType) =>
      val chartSpec = nameFieldMap.get(fieldName).fold(
        createChartSpecAux(Right(fieldName), chartType)
      )(field =>
        createChartSpecAux(Left(field), chartType)
      )
      (fieldName, chartSpec)
    }
  }

  private def createChartSpec(
    items: Traversable[JsObject],
    showLabels: Boolean = false,
    showLegend: Boolean = true)(
    fieldOrName: Either[Field, String],
    chartType: Option[ChartType.Value]
  ): ChartSpec = {
    val (fieldName, chartTitle, enumMap, fieldType) = fieldOrName match {
      case Left(field) => (
        field.name,
        field.label.getOrElse(fieldLabel(field.name)),
        field.numValues,
        field.fieldType
      )
      // failover... no corresponding field, providing default values instead
      case Right(fieldName) => (
        fieldName,
        fieldLabel(fieldName),
        None,
        FieldType.String
      )
    }

    fieldType match {
      case FieldType.String | FieldType.Enum | FieldType.Boolean => ChartSpec.categorical(
        getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend, chartType)

      case FieldType.Double | FieldType.Integer => ChartSpec.numerical(items, fieldName, chartTitle, 20, Some(1), None, None, chartType)

      case _ => ChartSpec.categorical(getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend)
    }
  }

  private def getFields(fieldNames: Traversable[String]): Future[Traversable[Field]] = {
    fieldRepo.find(Seq(FieldIdentity.name #=> fieldNames.toSeq))
  }

  private def getStringValues(
    items: Traversable[JsObject],
    fieldName: String
  ) =
    toStrings(project(items.toSeq, fieldName))

  private def toStrings(rawValues: Traversable[JsReadable]): Traversable[Option[String]] =
    rawValues.map{rawValue =>
      if (rawValue == JsNull)
        None
      else {
        val booleanValue = rawValue.asOpt[Boolean]
        if (booleanValue.isDefined)
          Some(booleanValue.get.toString)
        else
          rawValue.asOpt[String]
      }
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
    xFieldNameOption : Option[String],
    yFieldNameOption: Option[String],
    groupFieldNameOption: Option[String],
    filter: Seq[FilterCondition]
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    // initialize x, y, and group field names
    val xFieldName = xFieldNameOption.getOrElse(setting.defaultScatterXFieldName)
    val yFieldName = yFieldNameOption.getOrElse(setting.defaultScatterYFieldName)
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
    val numericFieldsFuture = fieldRepo.find(Seq("fieldType" #=> numericTypes))
    val categoricalFieldsFuture = fieldRepo.find(Seq("fieldType" #=> categoricalTypes))

    for {
      xField <- xFieldFuture
      yField <- yFieldFuture
      groupField <- groupFieldFuture
      numericFields <- numericFieldsFuture
      categoricalFields <- categoricalFieldsFuture
      scattedData <- getScatterData(filter, xField, yField, groupField)
    } yield {
      val numericFieldNameLabels = numericFields.map(field => (field.name, field.label)).toSeq.sorted
      val categoricalFieldNameLabels = categoricalFields.map(field => (field.name, field.label)).toSeq.sorted

      render {
        case Accepts.Html() => Ok(dataset.scatterStats(
          dataSetName,
          xFieldName,
          yFieldName,
          groupFieldName,
          numericFieldNameLabels,
          categoricalFieldNameLabels,
          scattedData,
          filter,
          router,
          dataSetId,
          getMetaInfos
        ))
        case Accepts.Json() => BadRequest("GetScatterStats function doesn't support JSON response.")
      }
    }
  }

  private def getScatterData(
    filter: Seq[FilterCondition],
    xField: Option[Field],
    yField: Option[Field],
    groupField: Option[Field]
  ): Future[Seq[(String, Seq[(Any, Any)])]] = {
    if (xField.isDefined && yField.isDefined) {
      val xFieldName = xField.get.name
      val yFieldName = yField.get.name

      val criteria = toCriteria(filter)
      val projection = Seq(xFieldName, yFieldName) ++ groupField.map(_.name)
      val futureXYZItems = repo.find(criteria, Nil, projection.toSet)

      futureXYZItems.map{ xyzItems =>
        val xyzSeq = xyzItems.toSeq
        val xValues = projectDouble(xyzSeq, xFieldName)
        val yValues = projectDouble(xyzSeq, yFieldName)

        groupField match {
          case Some(zField) => {
            val groupValues = projectString(xyzSeq, zField.name)
            // TODO: simplify this
            (groupValues, xValues, yValues).zipped.map { case (zValue, xValue, yValue) =>
              (zValue, xValue, yValue).zipped
            }.flatten.groupBy(_._1).map{ case (zValue, values) =>
              (
                zField.numValues.map(_.get(zValue)).flatten.getOrElse(
                  if (zValue.trim.isEmpty)
                    "N/A"
                  else
                    zValue
                ),
                values.map(tupple => (tupple._2, tupple._3))
              )
            }.toSeq
          }
          case None => {
            val xys = (xValues, yValues).zipped.map { case (xValue, yValue) =>
              (xValue, yValue).zipped
            }.flatten

            Seq(("all", xys))
          }
        }
      }
    } else
      Future(Seq[(String, Seq[(Any, Any)])]())
  }

  override def getDistribution(
    fieldNameOption: Option[String],
    filter: Seq[FilterCondition]
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val fieldName = fieldNameOption.getOrElse(setting.defaultDistributionFieldName)
    val chartSpecsFuture = getDataChartSpecs(
      toCriteria(filter),
      Seq(FieldChartType(fieldName, None)),
      true,
      false
    )

    {
      for {
        chartSpecs <- chartSpecsFuture
        fields <-fieldRepo.find(sort = Seq(AscSort("name")))
      } yield {
        val fieldNameLabels = fields.map(field => (field.name, field.label)).toSeq

        render {
          case Accepts.Html() => Ok(dataset.distribution(
            dataSetName, fieldName, fieldNameLabels, chartSpecs.head._2, filter, router, dataSetId, getMetaInfos
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

  override def getDateCount(
    dateFieldNameOption: Option[String],
    groupFieldName: Option[String],
    filter: Seq[FilterCondition]
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val dateFieldName = dateFieldNameOption.getOrElse(setting.defaultDateCountFieldName)

    val dateFieldFuture = fieldRepo.get(dateFieldName)
    val groupFieldFuture = groupFieldName.map(fieldRepo.get).getOrElse(Future(None))
    val dateFieldsFuture = fieldRepo.find(Seq("fieldType" #== FieldType.Date))
    val categoricalFieldsFuture = fieldRepo.find(Seq("fieldType" #=> categoricalTypes))

    {
      for {
        dateField <- dateFieldFuture
        groupField <- groupFieldFuture
        dateFields <- dateFieldsFuture
        categoricalFields <- categoricalFieldsFuture
        series <- dateField match {
          case Some(dateField) => getDateCountData(filter, dateField, groupField)
          case None => Future(Nil)
        }
      } yield {
        val dateFieldNameLabels = dateFields.map(field => (field.name, field.label)).toSeq.sorted
        val categoricalFieldNameLabels = categoricalFields.map(field => (field.name, field.label)).toSeq.sorted

        render {
          case Accepts.Html() => Ok(dataset.dateCount(
            dataSetName, dateFieldName, groupFieldName, dateFieldNameLabels, categoricalFieldNameLabels, series, filter, router, dataSetId, getMetaInfos
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
  ): Future[Seq[(String, Seq[(Date, Any)])]] = {
    val dateFieldName = dateField.name

    val criteria = toCriteria(filter)
    val projection = Seq(dateFieldName) ++ groupField.map(_.name)
    val dateGroupItemsFuture = repo.find(criteria, Nil, projection.toSet)

    val groupDatesFuture: Future[Seq[(String, Seq[Date])]] = dateGroupItemsFuture.map { dateGroupItems =>
      val dateGroupSeq = dateGroupItems.toSeq
      val dates = projectDate(dateGroupSeq, dateFieldName)

      groupField match {
        case Some(zField) => {
          val groups = projectString(dateGroupSeq, zField.name)

          // TODO: simplify this
          (groups, dates).zipped.map { case (group, date) =>
            (group, date).zipped
          }.flatten.groupBy(_._1).map { case (group, values) =>
            val groupName =
              zField.numValues.map(_.get(group)).flatten.getOrElse(
                if (group.trim.isEmpty)
                  "N/A"
                else
                  group
              )

            (groupName, values.map(_._2))
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

  override def getFieldNames = Action.async { implicit request =>
    for {
      fieldNames <- fieldRepo.find(sort = Seq(AscSort("name"))).map(_.map(_.name))
    } yield
      Ok(Json.toJson(fieldNames))
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

  /**
    * TODO: implement. what is it supposed to return?
    *
    * @param value ???
    * @param fieldType ???
    */
  private def readJsValueTyped(value : JsValue, fieldType : FieldType.Value) {

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

  // TODO: keep async
  private def getMetaInfos: Traversable[DataSpaceMetaInfo] =
    result(dataSpaceMetaInfoRepo.find())

  //////////////////////
  // Export Functions //
  //////////////////////


  /**
    * Generate and content of TRANSMART data file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  def exportTranSMARTDataFile(delimiter : String): Action[AnyContent] = Action { implicit request =>
    val fileContent = generateTranSMARTDataFile(tranSMARTDataFileName, delimiter, setting.exportOrderByFieldName)
    stringToFile(tranSMARTDataFileName)(fileContent)
  }


  /**
    * Generate content of TRANSMART mapping file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  def exportTranSMARTMappingFile(delimiter : String): Action[AnyContent] = Action { implicit request =>
    val fileContent = generateTranSMARTMappingFile(tranSMARTDataFileName, delimiter, setting.exportOrderByFieldName)
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
  ): String = {
    val recordsFuture = repo.find(sort = orderBy.fold(Seq[Sort]())(toSort))
    val records = result(recordsFuture)
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val categoriesFuture = categoryRepo.find()

    tranSMARTService.createClinicalDataFile(unescapedDelimiter, csvEOL, setting.tranSMARTReplacements)(
      records,
      setting.keyFieldName,
      setting.tranSMARTVisitFieldName,
      result(fieldNameCategoryMap(categoriesFuture))
    )
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
  ): String = {
    val dataSetSetting = setting
    val recordsFuture = repo.find(sort = orderBy.fold(Seq[Sort]())(toSort))
    val records = result(recordsFuture)
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val categoriesFuture = categoryRepo.find()

    tranSMARTService.createMappingFile(unescapedDelimiter, csvEOL, dataSetSetting.tranSMARTReplacements)(
      records,
      dataFilename,
      dataSetSetting.keyFieldName,
      dataSetSetting.tranSMARTVisitFieldName,
      result(fieldNameCategoryMap(categoriesFuture)),
      result(rootCategoryTree(categoriesFuture)),
      result(fieldLabelMap)
    )
  }

  protected def fieldNameCategoryMap(
    categoriesFuture: Future[Traversable[Category]]
  ): Future[Map[String, Category]] = {
    val idCategoriesFuture = categoriesFuture.map(_.map{ category =>
      (category._id.get, category)
    })

    val fieldsWithCategoryFuture = fieldRepo.find(
      criteria = Seq("categoryId" #!= None),
      projection = Seq("name", "categoryId", "fieldType")
    )

    for {
      idCategories <- idCategoriesFuture
      fieldsWithCategories <- fieldsWithCategoryFuture
    } yield {
      val idCategoriesMap = idCategories.toMap
      fieldsWithCategories.map(field =>
        (field.name, idCategoriesMap(field.categoryId.get))
      ).toMap
    }
  }

  protected def fieldLabelMap: Future[Map[String, String]] =
    for {
      fields <- fieldRepo.find(projection = Seq("name", "label", "fieldType"))
    } yield
      fields.map{ field =>
        (field.name, field.label.getOrElse(field.name))
      }.toMap

  protected def rootCategoryTree(
    categoriesFuture: Future[Traversable[Category]]
  ): Future[Category] =
    for {
      categories <- categoriesFuture
    } yield {
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