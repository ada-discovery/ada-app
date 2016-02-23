package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.util.{FilterSpec, JsonUtil, ChartSpec, FieldChartSpec}
import org.apache.commons.lang3.StringEscapeUtils
import models.{Page, FieldType, Field}
import persistence.{AscSort, DictionaryFieldRepo}
import play.api.routing.Router
import play.api.{Routes, Logger}
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, AnyContent, RequestHeader}
import play.api.Play.current
import play.twirl.api.Html
import util.WebExportUtil.stringToFile
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaTranSMARTMapping._
import services.TranSMARTService
import views.html.dataset
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}
import _root_.util.JsonUtil._
import scala.concurrent.duration._

trait DataSetController {

  def dataSetId: String

  def get(id: BSONObjectID): Action[AnyContent]

  def find(page: Int, orderBy: String, filter: FilterSpec): Action[AnyContent]

  def listAll(orderBy: Int): Action[AnyContent]

  def exportAllRecordsAsCsv(delimiter : String): Action[AnyContent]

  def exportAllRecordsAsJson: Action[AnyContent]

  def exportRecordsAsCsv(delimiter : String, filter: FilterSpec): Action[AnyContent]

  def exportRecordsAsJson(filter: FilterSpec): Action[AnyContent]

  def overviewFieldTypes: Action[AnyContent]

  def overview: Action[AnyContent]

  def overviewList(page: Int, orderBy: String, filter: FilterSpec): Action[AnyContent]

  def getScatterStats(xFieldName: Option[String], yFieldName: Option[String]): Action[AnyContent]

  def getDistribution(fieldName: Option[String]): Action[AnyContent]

  def exportTranSMARTDataFile(delimiter : String): Action[AnyContent]

  def exportTranSMARTMappingFile(delimiter : String): Action[AnyContent]

  def getFieldNames: Action[AnyContent]
}

protected abstract class DataSetControllerImpl(dictionaryRepo: DictionaryFieldRepo)
  extends ReadonlyController[JsObject, BSONObjectID](dictionaryRepo.dataRepo) with DataSetController with ExportableAction[JsObject] {

  dictionaryRepo.initIfNeeded

  @Inject var tranSMARTService: TranSMARTService = _

  // hooks

  protected def dataSetName: String

  // keyfield, mainly used for data export
  protected def keyField: String

  // reference field for sorting the data
  protected def exportOrderByField : String

  // auto-generated filename for csv files
  protected def csvFileName: String = dataSetName.replace(" ", "-") + ".csv"

  // auto-generated filename for json files
  protected def jsonFileName: String = dataSetName.replace(" ", "-") + ".json"

  // auto-generated filename for tranSMART data files
  protected def tranSMARTDataFileName: String = dataSetName.replace(" ", "-") + "_data_file"

  // auto-generated filename for tranSMART mapping files
  protected def tranSMARTMappingFileName: String = dataSetName.replace(" ", "-") + "_mapping_file"

  // key for associated field in config file
  protected def overviewFieldNamesConfPrefix: String

  // for scatter plot
  protected def defaultScatterXFieldName: String
  protected def defaultScatterYFieldName: String

  // for distribution plot
  protected def defaultDistributionFieldName: String

  // router for requests; to be passed to views as helper.
  protected lazy val router: DataSetRouter = DataSetRouter(dataSetId)

  protected lazy val overviewFieldNames =
    current.configuration.getStringSeq(overviewFieldNamesConfPrefix + ".overview.fieldnames").getOrElse(Seq[String]())

  private val jsonNumericTypes = Json.arr(FieldType.Double.toString, FieldType.Integer.toString)

  /**
    * Table displaying given paginated content. Generally used to display fields of the datasets.
    *
    * @param page Page object containing info (number of pages, current page, ...) for pagination. Contains JsObject represenation of data for display.
    * @param msg Internal request message.
    * @param request Header of original request.
    * @return View for all available fields.
    */
  override protected def listView(page: Page[JsObject])(implicit msg: Messages, request: RequestHeader) =
    dataset.list(
      dataSetName + " Item",
      page,
      Await.result(getFieldLabelMap(listViewColumns.get), 120000 millis), // TODO: refactor
      listViewColumns.get,
      router
    )

  /**
    * Table displaying given paginated content with charts on the top. Generally used to display fields of the datasets.
    *
    * @param page Page object containing info (number of pages, current page, ...) for pagination. Contains JsObject represenantion of data for display.
    * @param msg Internal request message.
    * @param request Header of original request.
    * @return View for all available fields.
    */
  private def overviewListView(page: Page[JsObject], fieldChartSpecs : Iterable[FieldChartSpec])(implicit msg: Messages, request: RequestHeader) =
    dataset.overviewList(
      dataSetName + " Item",
      page,
      Await.result(getFieldLabelMap(listViewColumns.get), 120000 millis),  // TODO: refactor
      listViewColumns.get,
      fieldChartSpecs,
      router
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
  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    dataset.show(
      dataSetName + " Item",
      item,
      router.plainList
    )

  /**
    * Generate content of csv export file and create download.
    *
    * @param delimiter Delimiter for csv output file.
    * @return View for download.
    */
  override def exportAllRecordsAsCsv(delimiter : String) =
    exportAllToCsv(csvFileName, delimiter, exportOrderByField)

  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  override def exportAllRecordsAsJson =
    exportAllToJson(jsonFileName, exportOrderByField)

  /**
    * Generate content of csv export file and create download.
    *
    * @param delimiter Delimiter for csv output file.
    * @return View for download.
    */
  override def exportRecordsAsCsv(delimiter : String, filter: FilterSpec) =
    exportToCsv(csvFileName, delimiter, filter.toJsonCriteria, exportOrderByField)

  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  override def exportRecordsAsJson(filter: FilterSpec) =
    exportToJson(jsonFileName, filter.toJsonCriteria, exportOrderByField)

  /**
    * Fetch specified field (column) entries from repo and wrap them in a Traversable object.
    * If a field contains an empty json, it is represented by None.
    *
    * @param fieldName Name of the field of interest.
    * @return Traversable object containing the String entries of the field.
    */
  private def getFieldValues(fieldName : String): Future[Traversable[Option[String]]] = {
    for {
      field <- dictionaryRepo.get(fieldName)
      values <- repo.find(None, None, Some(Json.obj(fieldName -> 1)))
    } yield {
      values.map { item =>
        val jsValue: JsValue = (item \ fieldName).get
        JsonUtil.toString(jsValue)
      }
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
    dictionaryRepo.find().map{ fields =>
      if (fields.isEmpty)
        throw new IllegalStateException(s"Empty dictionary found. Pls. create one by running 'standalone.InferXXXDictionary' script.")

      val fieldTypeCounts = ArrayBuffer.fill(FieldType.values.size)(0)
      fields.foreach { field =>
        fieldTypeCounts(field.fieldType.id) += 1
      }

      implicit val msg = messagesApi.preferred(request)
      render {
        case Accepts.Html() => Ok(views.html.dataset.typeOverview(dataSetName, (FieldType.values, fieldTypeCounts).zipped.toList))
        case Accepts.Json() => Ok(JsObject(
          (FieldType.values, fieldTypeCounts).zipped.map{ case (fieldType, count) =>
            (fieldType.toString, JsNumber(count))
          }.toSeq
        ))
      }
    }
  }

  def overview = Action.async { implicit request =>
    val futureChartSpecs = overviewFieldNames.map(getDataChartSpec(None, _))

    Future.sequence(futureChartSpecs).map { chartSpecs =>
      implicit val msg = messagesApi.preferred(request)
      Ok(dataset.overview(dataSetName + " Overview", chartSpecs))
    }
  }

  override def overviewList(page: Int, orderBy: String, filter: FilterSpec) = Action.async { implicit request =>
    val futureFieldChartSpecs = overviewFieldNames.map(fieldName =>
      getDataChartSpec(filter.toJsonCriteria, fieldName).map(chartSpec =>
        FieldChartSpec(fieldName, chartSpec)
      )
    )

    val (futureItems, futureCount) = getFutureItemsAndCount(page, orderBy, filter)

    futureItems.zip(futureCount).zip(Future.sequence(futureFieldChartSpecs)).map{
      case ((items, count), fieldChartSpecs) => {
        implicit val msg = messagesApi.preferred(request)

        render {
          case Accepts.Html() => Ok(overviewListView(Page(items, page, page * limit, count, orderBy, filter), fieldChartSpecs))
          case Accepts.Json() => Ok(Json.toJson(items))
        }
    }}.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the overviewList process")
        InternalServerError(t.getMessage)
    }
  }

  /**
    * Infers which chart type to use for a field by checking its type.
    * Strings are identified as pie charts.
    * Doubles and Integers are identified as Barplots.
    * All plots which can not be matched are identified as pie charts.
    *
    * @param fieldName Name of the field. Used to find field in data repo.
    * @return Inferred chart type.
    */
  private def getDataChartSpec(
    criteria: Option[JsObject],
    fieldName: String,
    showLabels: Boolean = false,
    showLegend: Boolean = true
  ) : Future[ChartSpec] =
    dictionaryRepo.get(fieldName).flatMap { foundField =>
      if (foundField.isDefined) {
        val chartTitle = foundField.get.label.getOrElse(fieldName)
        val enumMap = foundField.get.numValues

        repo.find(criteria, None, Some(Json.obj(fieldName -> 1))).map { items =>
          foundField.get.fieldType match {
            case FieldType.String => ChartSpec.pie(getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend)
            case FieldType.Enum => ChartSpec.pie(getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend)
            case FieldType.Double => ChartSpec.column(items, fieldName, chartTitle, 20)
            case FieldType.Integer => ChartSpec.column(items, fieldName, chartTitle, 20)
            case _ => ChartSpec.pie(getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend)
          }
        }
      } else
        Future(ChartSpec.pie(Seq(), None, fieldName, showLabels, showLegend))
    }

  private def getStringValues(
    items: Traversable[JsObject],
    fieldName: String
  ) =
    toStrings(project(items.toSeq, fieldName))

  private def toStrings(rawValues: Traversable[JsReadable]) =
    rawValues.map{rawWalue =>
      if (rawWalue == JsNull)
        "null"
      else
        rawWalue.as[String]
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
  override def getScatterStats(xFieldNameOption : Option[String], yFieldNameOption: Option[String]) = Action.async { implicit request =>
    val xFieldName = xFieldNameOption.getOrElse(defaultScatterXFieldName)
    val yFieldName = yFieldNameOption.getOrElse(defaultScatterYFieldName)

    val numericFieldsFuture = dictionaryRepo.find(Some(Json.obj("fieldType" -> Json.obj("$in" -> jsonNumericTypes))))
    val xFieldFuture = dictionaryRepo.get(xFieldName)
    val yFieldFuture = dictionaryRepo.get(yFieldName)

    numericFieldsFuture.zip(xFieldFuture).zip(yFieldFuture).flatMap{ case ((numericFields, xField), yField) =>
      implicit val msg = messagesApi.preferred(request)

      val numericFieldNameLabels = numericFields.map(field => (field.name, field.label)).toSeq.sorted
      val valuesFuture : Future[Seq[(Any, Any)]] = if (xField.isDefined && yField.isDefined) {
        val futureXItems = repo.find(None, None, Some(Json.obj(xFieldName -> 1)))
        val futureYItems = repo.find(None, None, Some(Json.obj(yFieldName -> 1)))

        futureXItems.zip(futureYItems).map{ case (xItems, yItems) =>
          val xValues = projectDouble(xItems.toSeq, xFieldName)
          val yValues = projectDouble(yItems.toSeq, yFieldName)
          (xValues, yValues).zipped.map{ case (xValue, yValue) => (xValue, yValue).zipped}.flatten
        }
      } else
        Future(Seq[(Any, Any)]())

      valuesFuture.map( values =>

        render {
          case Accepts.Html() => Ok(dataset.scatterStats(dataSetName, xFieldName, yFieldName, router.getScatterStats, dataSetId, numericFieldNameLabels, values))
          case Accepts.Json() => BadRequest("GetScatterStats function doesn't support JSON response.")
        }
      )
    }
  }

  override def getDistribution(fieldNameOption: Option[String]) = Action.async { implicit request =>
    val fieldName = fieldNameOption.getOrElse(defaultDistributionFieldName)

    val fieldsFuture = dictionaryRepo.find(None, Some(Seq(AscSort("name"))))
    val chartSpecFuture = getDataChartSpec(None, fieldName, true, false)
    chartSpecFuture.zip(fieldsFuture).map{ case (chartSpec, fields) =>
      implicit val msg = messagesApi.preferred(request)
      val fieldNameLabels = fields.map(field => (field.name, field.label)).toSeq

      render {
        case Accepts.Html() => Ok(dataset.distribution(dataSetName, fieldName, chartSpec, router.getDistribution, dataSetId, fieldNameLabels))
        case Accepts.Json() => BadRequest("GetDistribution function doesn't support JSON response.")
      }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the distribution process")
        InternalServerError(t.getMessage)
    }
  }


  override def getFieldNames = Action.async { implicit request =>
    val futureFieldNames = dictionaryRepo.find(None, Some(Seq(AscSort("name")))).map(_.map(_.name))
    futureFieldNames.map(fieldNames => Ok(Json.toJson(fieldNames)))
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
        dictionaryRepo.get(fieldName).map { fieldOption =>
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
  def exportTranSMARTDataFile(delimiter : String): Action[AnyContent] = Action { implicit request =>
    val fileContent = generateTranSMARTDataFile(tranSMARTDataFileName, delimiter, exportOrderByField)
    stringToFile(tranSMARTDataFileName)(fileContent)
  }


  /**
    * Generate content of TRANSMART mapping file and create a download.
    *
    * @param delimiter Delimiter for output file.
    * @return View for download.
    */
  def exportTranSMARTMappingFile(delimiter : String): Action[AnyContent] = Action { implicit request =>
    val fileContent = generateTranSMARTMappingFile(tranSMARTDataFileName, delimiter, exportOrderByField)
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
  protected def generateTranSMARTDataFile(dataFilename: String, delimiter: String, orderBy : String): String

  /**
    * Generate the content of TRANSMART mapping file for downnload.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return VString with file content.
    */
  protected def generateTranSMARTMappingFile(dataFilename: String, delimiter: String, orderBy : String): String
}