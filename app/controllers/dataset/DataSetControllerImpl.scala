package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import _root_.util.JsonUtil._
import _root_.util.WebExportUtil._
import _root_.util.{ChartSpec, FieldChartSpec, FilterSpec, JsonUtil}
import controllers.{ExportableAction, ReadonlyController}
import models.{DataSetMetaInfo, FieldType, Page, Field, Category}
import org.apache.commons.lang3.StringEscapeUtils
import persistence.AscSort
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.Play._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, RequestHeader}
import reactivemongo.bson.BSONObjectID
import services.TranSMARTService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.json.BSONObjectIDFormat
import scala.concurrent.duration._
import views.html.dataset

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.Await.result

protected abstract class DataSetControllerImpl(
    val dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends ReadonlyController[JsObject, BSONObjectID](dsaf(dataSetId).get.dataSetRepo) with DataSetController with ExportableAction[JsObject] {

  protected val dsa: DataSetAccessor = dsaf(dataSetId).get
  protected val fieldRepo = dsa.fieldRepo
  fieldRepo.initIfNeeded

  protected val categoryRepo = dsa.categoryRepo

  @Inject protected var tranSMARTService: TranSMARTService = _

  // hooks

  protected lazy val dataSetName = result(dsa.metaInfo).name

  // keyfield, mainly used for data export
  protected def keyField: String

  // reference field for sorting the data
  protected def exportOrderByField : String

  // auto-generated filename for csv files
  protected def csvFileName: String = dataSetId.replace(" ", "-") + ".csv"

  // auto-generated filename for json files
  protected def jsonFileName: String = dataSetId.replace(" ", "-") + ".json"

  // auto-generated filename for tranSMART data files
  protected def tranSMARTDataFileName: String = dataSetId.replace(" ", "-") + "_data_file"

  // auto-generated filename for tranSMART mapping files
  protected def tranSMARTMappingFileName: String = dataSetId.replace(" ", "-") + "_mapping_file"

  // visit field for transmart
  protected def tranSMARTVisitField: Option[String] = None

  protected def tranSMARTReplacements = List[(String, String)]()

  // key for associated field in config file
  protected def overviewFieldNamesConfPrefix: String

  // for scatter plot
  protected def defaultScatterXFieldName: String
  protected def defaultScatterYFieldName: String

  // for distribution plot
  protected def defaultDistributionFieldName: String

  // router for requests; to be passed to views as helper.
  protected lazy val router: DataSetRouter = new DataSetRouter(dataSetId)

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
  private def overviewListView(page: Page[JsObject], fieldChartSpecs : Iterable[FieldChartSpec])(implicit msg: Messages, request: RequestHeader) =
    dataset.overviewList(
      dataSetName + " Item",
      page,
      result(getFieldLabelMap(listViewColumns.get)),  // TODO: refactor
      listViewColumns.get,
      fieldChartSpecs,
      router,
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
      field <- fieldRepo.get(fieldName)
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

  def overview = Action.async { implicit request =>
    val futureChartSpecs = overviewFieldNames.map(getDataChartSpec(None, _))

    Future.sequence(futureChartSpecs).map { chartSpecs =>
      implicit val msg = messagesApi.preferred(request)
      Ok(dataset.overview(dataSetName + " Overview", chartSpecs))
    }
  }

  override def overviewList(page: Int, orderBy: String, filter: FilterSpec) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val futureFieldChartSpecs = overviewFieldNames.map(fieldName =>
      getDataChartSpec(filter.toJsonCriteria, fieldName).map(chartSpec =>
        FieldChartSpec(fieldName, chartSpec)
      )
    )
    val fieldChartSpecsFuture = Future.sequence(futureFieldChartSpecs)
    val (futureItems, futureCount) = getFutureItemsAndCount(page, orderBy, filter)

    {
      for {
        items <- futureItems
        count <- futureCount
        fieldChartSpecs <- fieldChartSpecsFuture
      } yield
        render {
          case Accepts.Html() => Ok(overviewListView(Page(items, page, page * limit, count, orderBy, filter), fieldChartSpecs))
          case Accepts.Json() => Ok(Json.toJson(items))
        }
    }.recover {
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
  ): Future[ChartSpec] =
    for {
      Some(foundField) <- fieldRepo.get(fieldName)
      items <- repo.find(criteria, None, Some(Json.obj(fieldName -> 1)))
    } yield {
      val chartTitle = foundField.label.getOrElse(fieldName)
      val enumMap = foundField.numValues

      foundField.fieldType match {
        case FieldType.String => ChartSpec.pie(getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend)
        case FieldType.Enum => ChartSpec.pie(getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend)
        case FieldType.Boolean => ChartSpec.pie(getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend)
        case FieldType.Double => ChartSpec.column(items, fieldName, chartTitle, 20)
        case FieldType.Integer => ChartSpec.column(items, fieldName, chartTitle, 20)
        case _ => ChartSpec.pie(getStringValues(items, fieldName), enumMap, chartTitle, showLabels, showLegend)
      }
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
    filter: FilterSpec
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val xFieldName = xFieldNameOption.getOrElse(defaultScatterXFieldName)
    val yFieldName = yFieldNameOption.getOrElse(defaultScatterYFieldName)

    val numericFieldsFuture = fieldRepo.find(Some(Json.obj("fieldType" -> Json.obj("$in" -> jsonNumericTypes))))
    val xFieldFuture = fieldRepo.get(xFieldName)
    val yFieldFuture = fieldRepo.get(yFieldName)

    numericFieldsFuture.zip(xFieldFuture).zip(yFieldFuture).flatMap{ case ((numericFields, xField), yField) =>
      val numericFieldNameLabels = numericFields.map(field => (field.name, field.label)).toSeq.sorted
      val valuesFuture : Future[Seq[(Any, Any)]] = if (xField.isDefined && yField.isDefined) {
        val criteria = filter.toJsonCriteria

        val futureXYItems = repo.find(criteria, None, Some(Json.obj(xFieldName -> 1, yFieldName -> 1)))

        futureXYItems.map{ xyItems =>
          val xySeq = xyItems.toSeq
          val xValues = projectDouble(xySeq, xFieldName)
          val yValues = projectDouble(xySeq, yFieldName)
          (xValues, yValues).zipped.map{ case (xValue, yValue) => (xValue, yValue).zipped}.flatten
        }
      } else
        Future(Seq[(Any, Any)]())

      valuesFuture.map( values =>
        render {
          case Accepts.Html() => Ok(dataset.scatterStats(
            dataSetName,
            xFieldName,
            yFieldName,
            filter,
            router,
            dataSetId,
            numericFieldNameLabels,
            values,
            getMetaInfos
          ))
          case Accepts.Json() => BadRequest("GetScatterStats function doesn't support JSON response.")
        }
      )
    }
  }

  override def getDistribution(
    fieldNameOption: Option[String],
    filter: FilterSpec
  ) = Action.async { implicit request =>
    implicit val msg = messagesApi.preferred(request)

    val fieldName = fieldNameOption.getOrElse(defaultDistributionFieldName)
    val fieldsFuture = fieldRepo.find(None, Some(Seq(AscSort("name"))))
    val fieldNameLabelsFuture = fieldsFuture.map(_.map(field => (field.name, field.label)).toSeq)
    val chartSpecFuture = getDataChartSpec(filter.toJsonCriteria, fieldName, true, false)

    {
      for {
        chartSpec <- chartSpecFuture
        fieldNameLabels <- fieldNameLabelsFuture
      } yield
        render {
          case Accepts.Html() => Ok(dataset.distribution(
            dataSetName, fieldName, chartSpec, filter, router, dataSetId, fieldNameLabels, getMetaInfos
          ))
          case Accepts.Json() => BadRequest("GetDistribution function doesn't support JSON response.")
        }
    }.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the distribution process")
        InternalServerError(t.getMessage)
    }
  }


  override def getFieldNames = Action.async { implicit request =>
    for {
      fieldNames <- fieldRepo.find(None, Some(Seq(AscSort("name")))).map(_.map(_.name))
    } yield
      Ok(Json.toJson(fieldNames))
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

  // TODO: keep as async
  private def getMetaInfos: Traversable[DataSetMetaInfo] =
    result(dataSetMetaInfoRepo.find())

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
  protected def generateTranSMARTDataFile(
    dataFilename: String,
    delimiter: String,
    orderBy : String
  ): String = {
    val recordsFuture = repo.find(None, toSort(orderBy), None, None, None)
    val records = result(recordsFuture)
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val categoriesFuture = categoryRepo.find()

    tranSMARTService.createClinicalDataFile(unescapedDelimiter , "\n", tranSMARTReplacements)(
      records,
      keyField,
      tranSMARTVisitField,
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
    orderBy : String
  ): String = {
    val recordsFuture = repo.find(None, toSort(orderBy), None, None, None)
    val records = result(recordsFuture)
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    val categoriesFuture = categoryRepo.find()

    tranSMARTService.createMappingFile(unescapedDelimiter , "\n", tranSMARTReplacements)(
      records,
      dataFilename,
      keyField,
      tranSMARTVisitField,
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

    val fieldsWithCategoryFuture = fieldRepo.find(Some(
      Json.obj("categoryId" -> Json.obj("$ne" -> Option.empty[BSONObjectID]))
    ))

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
      fields <- fieldRepo.find()
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