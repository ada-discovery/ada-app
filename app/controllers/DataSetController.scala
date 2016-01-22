package controllers

import javax.inject.Inject

import _root_.util.{JsonUtil, ChartSpec}
import org.apache.commons.lang3.StringEscapeUtils
import models.{Page, FieldType}
import persistence.DictionaryFieldRepo
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{AnyContent, RequestHeader, Action}
import play.api.Play.current
import util.WebExportUtil.stringToFile
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaTranSMARTMapping._
import services.TranSMARTService
import views.html.dataset
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}
import _root_.util.JsonUtil._

protected abstract class DataSetController(dictionaryRepo: DictionaryFieldRepo)
  extends ReadonlyController[JsObject, BSONObjectID](dictionaryRepo.dataRepo) with ExportableAction[JsObject] {

  dictionaryRepo.initIfNeeded

  @Inject var tranSMARTService: TranSMARTService = _

  // hooks
  protected def dataSetName : String

  // keyfield, mainly used for data export
  protected def keyField : String

  // reference field for sorting the data
  protected def exportOrderByField : String

  // auto-generated filename for csv files
  protected def csvFileName : String = dataSetName.replace(" ", "-") + ".csv"

  // auto-generated filename for json files
  protected def jsonFileName : String = dataSetName.replace(" ", "-") + ".json"

  // auto-generated filename for tranSMART data files
  protected def tranSMARTDataFileName : String = dataSetName.replace(" ", "-") + "_data_file"

  // auto-generated filename for tranSMART mapping files
  protected def tranSMARTMappingFileName : String = dataSetName.replace(" ", "-") + "_mapping_file"

  // key for associated field in config file
  protected def overviewFieldNamesConfPrefix : String

  // router for requests; to be passed to views as helper.
  protected def router : DataSetRouter


  /**
    * TODO: change and add actual querying.
    *
    * Turns String into JsObject compatible with reactiveMongo.
    * The JsObject is used for querzing and filtering the data.
    * Returns None, if no criteria given.
    *
    * @param string Input String to be converted.
    * @return JsObject containing the criteria.
    */
  override protected def toJsonCriteria(string : String) : Option[JsObject] =
  {
    if (!string.isEmpty)
      Some(Json.obj(keyField -> Json.obj("$regex" -> (string + ".*"), "$options" -> "i")))
    else
      None
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
  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    dataset.show(
      dataSetName + " Item",
      item,
      router.plainFindCall
    )


  /**
    * Table displaying given paginated content. Generally used to display fields of the datasets.
    *
    * @param currentPage Page object containing info (number of pages, current page, ...) for pagination. Contains JsObject represenation of data for display.
    * @param msg Internal request message.
    * @param request Header of original request.
    * @return View for all available fields.
    */
  override def listView(currentPage: Page[JsObject])(implicit msg: Messages, request: RequestHeader) ={
    //val fieldsFuture = dictionaryRepo.find()
    //val fieldNames = fieldsFuture.map{field => field.map(f => f.name)}
    dataset.list(
      dataSetName + " Item",
      currentPage,
      listViewColumns.get,
      router,
      listViewColumns.get,
      keyField
    )
  }


  /**
    * Generate content of csv export file and create download.
    *
    * @param delimiter Delimiter for csv output file.
    * @return View for download.
    */
  def exportRecordsAsCsv(delimiter : String) =
    exportAllToCsv(csvFileName, delimiter, exportOrderByField)


  /**
    * Generate content of Json export file and create donwload.
    *
    * @return View for download.
    */
  def exportRecordsAsJson =
    exportAllToJson(jsonFileName, exportOrderByField)

  /**
    * Fetch specified field (column) entries from repo and wrap them in a Traversable object.
    * If a field contains an empty json, it is represented by None.
    *
    * @param fieldName Name of the field of interest.
    * @return Traversable object containing the String entries of the field.
    */
  def getFieldValues(fieldName : String): Future[Traversable[Option[String]]] = {
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
  def overviewFieldTypes: Action[AnyContent] = Action.async { implicit request =>
    dictionaryRepo.find().map{ fields =>
      if (fields.isEmpty)
        throw new IllegalStateException(s"Empty dictionary found. Pls. create one by running 'standalone.InferXXXDictionary' script.")

      val fieldTypeCounts = ArrayBuffer.fill(FieldType.values.size)(0)
      fields.foreach { field =>
        fieldTypeCounts(field.fieldType.id) += 1
      }

      implicit val msg = messagesApi.preferred(request)
      Ok(views.html.dataset.typeOverview(dataSetName + " Fields", (FieldType.values, fieldTypeCounts).zipped.toList))
    }
  }


  def overview(fieldNames : Option[Seq[String]]) = Action.async { implicit request =>
    val futureChartSpecs = fieldNames.getOrElse {
      val strings = current.configuration.getStringSeq(overviewFieldNamesConfPrefix + ".overview.fieldnames").get
      strings
    }.map(getChartSpec)

    Future.sequence(futureChartSpecs).map { chartSpecs =>
      implicit val msg = messagesApi.preferred(request)
      Ok(dataset.overview(dataSetName + " Overview", chartSpecs))
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
  private def getChartSpec(fieldName : String) : Future[ChartSpec] =
    dictionaryRepo.get(fieldName).flatMap { foundField =>
      if (foundField.isDefined) {
        repo.find(None, None, Some(Json.obj(fieldName -> 1))).map(items =>
          foundField.get.fieldType match {
            case FieldType.String => ChartSpec.pie(items, fieldName)
            case FieldType.Double => ChartSpec.column(items, fieldName, 20)
            case FieldType.Integer => ChartSpec.column(items, fieldName, 20)
            case _ => ChartSpec.pie(items, fieldName)
          }
        )
      } else
        Future(ChartSpec.pie(List[JsObject](), fieldName))
    }


  /**
    * Fetches, checks and prepares the specified data fields for a scatterplot.
    * Only compatible FieldTypes (FieldType.Double and FieldType.Integer) are used.
    * Displays the resulting scatterplot in a view.
    *
    * @param xFieldName Name of field to be used for x coordinates.
    * @param yFieldName Name of field to be used for y coordinates.
    * @return View with scatterplot and selection option for different xFieldName and yFieldName.
    */
  def getScatterStats(xFieldName : String, yFieldName : String): Action[AnyContent] = Action.async { implicit request =>
    val fieldsFuture = dictionaryRepo.find()
    val xFieldFuture = dictionaryRepo.get(xFieldName)
    val yFieldFuture = dictionaryRepo.get(yFieldName)

    fieldsFuture.zip(xFieldFuture).zip(yFieldFuture).flatMap{ case ((fields, xField), yField) =>
      implicit val msg = messagesApi.preferred(request)

      val numericFields = fields.filter{field =>
        field.fieldType == FieldType.Double || field.fieldType == FieldType.Integer
      }
      val numericFieldNames = numericFields.map(_.name).toSeq.sorted
      val valuesFuture : Future[Seq[(Any, Any)]] = if (xField.isDefined && yField.isDefined) {
        val futureXItems = repo.find(None, None, Some(Json.obj(xFieldName -> 1)))
        val futureYItems = repo.find(None, None, Some(Json.obj(yFieldName -> 1)))

        futureXItems.zip(futureYItems).map{ case (xItems, yItems) =>
          val xValues = projectDouble(xItems.toSeq, xFieldName)
          val yValues = projectDouble(yItems.toSeq, yFieldName)
          (xValues, yValues).zipped.map{ case (xValue, yValue) =>
            if (xValue.isDefined && yValue.isDefined)
              Some(xValue.get, yValue.get)
            else
              None
          }.flatten
        }
      } else
        Future(Seq[(Any, Any)]())

      valuesFuture.map( values =>
        Ok(dataset.scatterStats(xFieldName, yFieldName, router.getScatterStatsCall, numericFieldNames, values))
      )
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
  protected def generateTranSMARTDataFile(dataFilename: String, delimiter: String, orderBy : String): String =
  {
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createClinicalDataFile(unescapedDelimiter , "\n", List[(String, String)]())(
      records, keyField, None, fieldCategoryMap)
  }


  /**
    * Generate the content of TRANSMART mapping file for downnload.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return VString with file content.
    */
  protected def generateTranSMARTMappingFile(dataFilename: String, delimiter: String, orderBy : String): String =
  {
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createMappingFile(unescapedDelimiter , "\n", List[(String, String)]())(
      records, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)
  }


  /**
    * Generate the content of TRANSMART data and mapping file for downnload.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return String with file content.
    */
  protected def getTranSMARTDataAndMappingFiles(dataFilename: String, delimiter: String, orderBy : String): (String, String) = {
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(
      records, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)
  }
}