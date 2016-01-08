package controllers

import javax.inject.Inject

import _root_.util.ChartSpec
import org.apache.commons.lang3.StringEscapeUtils
import models.{Page, FieldType}
import persistence.DictionaryFieldRepo
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{RequestHeader, Action}
import play.api.Play.current
import util.WebExportUtil.stringToFile
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaTranSMARTMapping._
import services.TranSMARTService
import views.html.dataset
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}

protected abstract class DataSetController(dictionaryRepo: DictionaryFieldRepo)
  extends ReadonlyController[JsObject, BSONObjectID](dictionaryRepo.dataRepo) with ExportableAction[JsObject] {

  dictionaryRepo.initIfNeeded

  @Inject var tranSMARTService: TranSMARTService = _

  // hooks
  protected def dataSetName : String

  protected def keyField : String

  protected def exportOrderByField : String

  protected def csvFileName : String = dataSetName.replace(" ", "-") + ".csv"

  protected def jsonFileName : String = dataSetName.replace(" ", "-") + ".json"

  protected def transSMARTDataFileName : String = dataSetName.replace(" ", "-") + "_data_file"

  protected def transSMARTMappingFileName : String = dataSetName.replace(" ", "-") + "_mapping_file"

  protected def overviewFiledNamesConfPrefix : String

  protected def router : DataSetRouter

  // generic show view
  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    dataset.show(
      dataSetName + " Overview",
      item,
      router.plainFindCall
    )

  // generic list view
  override def listView(currentPage: Page[JsObject])(implicit msg: Messages, request: RequestHeader) =
    dataset.list(
      dataSetName + " Item",
      currentPage,
      listViewColumns.get,
      router
    )

  def exportRecordsAsCsv(delimiter : String) =
    exportAllToCsv(csvFileName, delimiter, exportOrderByField)

  def exportRecordsAsJson =
    exportAllToJson(jsonFileName, exportOrderByField)

  /**
    * Fetch specified field (column) entries by name and wrap them in a JSObject.
    * @param fieldName Name of the field of interest.
    * @return JsObject containing the entries of the field.
    */
  def getFieldValues(fieldName : String) = {
    for {
      field <- dictionaryRepo.get(fieldName)
      values <- repo.find(None, None, Some(Json.obj(fieldName -> 1)))
    } yield {
      val fieldType = field.get.fieldType
      values.map { item =>
        val jsValue = (item \ fieldName).get
        jsValue match {
          case JsNull => None
          case x : JsString => Some(jsValue.as[String])
          case _ => Some(jsValue.toString)
        }
      }
    }
  }

  /**
    * Display field types in piechart.
    *
    */
  def overviewFieldTypes = Action.async { implicit request =>
    dictionaryRepo.find().map{ fields =>
      if (fields.isEmpty)
        throw new IllegalStateException(s"Empty dictionary found. Pls. create one by running 'standalone.InferXXXDictionary' script.")

      val fieldTypeCounts = ArrayBuffer.fill(FieldType.values.size)(0)
      fields.foreach { field =>
        fieldTypeCounts(field.fieldType.id) += 1
      }

      implicit val msg = messagesApi.preferred(request)
      Ok(views.html.dataset.typeOverview("Overview", (FieldType.values, fieldTypeCounts).zipped.toList))
    }
  }

  def overview(fieldNames : Option[Seq[String]]) = Action.async { implicit request =>
    val futureChartSpecs = fieldNames.getOrElse {
      val strings = current.configuration.getStringSeq(overviewFiledNamesConfPrefix + ".overview.fieldnames").get
      strings
    }.map(getChartSpec)

    Future.sequence(futureChartSpecs).map { chartSpecs =>
      implicit val msg = messagesApi.preferred(request)
      Ok(dataset.overview("Overview", chartSpecs))
    }
  }

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

  private def readJsValueTyped(value : JsValue, fieldType : FieldType.Value) {

  }

  /**
   * TranSMART functionality
   */
  def exportTranSMARTDataFile(delimiter : String) = Action { implicit request =>
    val fileContents = getTransSMARTDataAndMappingFiles(transSMARTDataFileName, delimiter, exportOrderByField)
    stringToFile(transSMARTDataFileName)(fileContents._1)
  }

  def exportTranSMARTMappingFile(delimiter : String) = Action { implicit request =>
    val fileContents = getTransSMARTDataAndMappingFiles(transSMARTDataFileName, delimiter, exportOrderByField)
    stringToFile(transSMARTMappingFileName)(fileContents._2)
  }

  protected def getTransSMARTDataAndMappingFiles(dataFilename: String, delimiter: String, orderBy : String) = {
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(
      records, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)
  }
}