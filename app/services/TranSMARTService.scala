package services

import javax.inject.{Inject, Singleton}

import _root_.util.JsonUtil.{jsonObjectsToCsv, escapeKey, unescapeKey}
import _root_.util.toHumanReadableCamel
import com.google.inject.ImplementedBy
import models.Category
import play.api.libs.json._

@ImplementedBy(classOf[TranSMARTServiceImpl])
trait TranSMARTService {

  /**
    * Takes fields and filters them according to parameters by either including or excluding fields.
    * Be aware that this call can not be made with both fieldsToInclude and fieldsToExclude being defined at the same time.
    *
    * @param items Input items to be filtered.
    * @param fieldsToInclude List of fields to be used for result generation.
    * @param fieldsToExclude List of fields to be exluded from result.
    * @return Items with defined entries included/ excluded.
    */
  def createClinicalData(
    items : Traversable[JsObject],
    fieldsToInclude : Option[List[String]],
    fieldsToExclude : Option[List[String]]
  ) : Traversable[JsObject]

  /**
    * Creates a template for the clinical mapping
    *
    * @param dataFileName Name of output file.
    * @param keyField Field for use as unique key for tranSMART mapping file.
    * @param visitField Field to be used as visit field in tranSMART mapping file.
    * @param fieldsInOrder Filtered input fields.
    * @param fieldCategoryMap Define which field names map to which tranSMART categories.
    * @param rootCategory Category to be used as tranSMART root.
    * @param fieldLabelMap (Re)map field to labels in tranSMART file.
    * @return Items containing the values for the clinical mapping file.
    */
  def createClinicalMapping(
    dataFileName : String,
    keyField : String,
    visitField : Option[String],
    fieldsInOrder : Iterable[String],
    fieldCategoryMap : Map[String, Category],
    rootCategory : Category,
    fieldLabelMap : Map[String, String]
  ) : Traversable[JsObject]

  /**
    * Deprecated.
    * Process input items to create clinical and mapping file.
    * Replace substrings and symbols if necessary, map columns to tranSMART properties and fields.
    *
    * @see createClinicalData()
    * @see createClinicalMapping()
    * @param delimiter String to use as entry delimiter.
    * @param newLine String to use as line delimiter.
    * @param replacements List of pairs for replacing strings and symbols of format: (input string, replacement).
    * @param items Items to be written into output file. May be modified with respect to the other parameters.
    * @param dataFileName Name of output file.
    * @param keyField Field for use as unique key for tranSMART mapping file.
    * @param visitField Field to be used as visit field in tranSMART mapping file.
    * @param fieldCategoryMap Define which field names map to which tranSMART categories.
    * @param rootCategory Category to be used as tranSMART root.
    * @param fieldLabelMap (Re)map field to labels in tranSMART file.
    * @return Pair containing the content for the tranSMART datafile and the tranSMART mapping file.
    */
  def createClinicalDataAndMappingFiles(
    delimiter : String,
    newLine : String,
    replacements : Iterable[(String, String)]
  )(
    items : Traversable[JsObject],
    dataFileName : String,
    keyField : String,
    visitField : Option[String],
    fieldCategoryMap : Map[String, Category],
    rootCategory : Category,
    fieldLabelMap : Map[String, String]
  ) : (String, String)


  /**
    * Generates the content for the clinical data file.
    * Replace substrings and symbols if necessary, map columns to tranSMART properties and fields.
    *
    * @param delimiter String to use as entry delimiter.
    * @param newLine String to use as line delimiter.
    * @param replacements List of pairs for replacing strings and symbols of format: (input string, replacement).
    * @param items Items to be written into output file. May be modified with respect to the other parameters.
    * @param keyField Field for use as unique key for tranSMART mapping file.
    * @param visitField Field to be used as visit field in tranSMART mapping file.
    * @param fieldCategoryMap Define which field names map to which tranSMART categories.
    * @return
    */
  def createClinicalDataFile(
      delimiter : String,
      newLine : String,
      replacements : Iterable[(String, String)]
    )(
      items : Traversable[JsObject],
      keyField : String,
      visitField : Option[String],
      fieldCategoryMap : Map[String, Category]
  ) : String


  /**
    * TODO: still inefficient, since processing the clinical data is required.
    *
    * Generate the content for the data mapping file.
    * Replace substrings and symbols if necessary, map columns to tranSMART properties and fields.
    *
    * @param delimiter String to use as entry delimiter.
    * @param newLine String to use as line delimiter.
    * @param replacements List of pairs for replacing strings and symbols of format: (input string, replacement).
    * @param items Items to be written into output file. May be modified with respect to the other parameters.
    * @param dataFileName Name of output file.
    * @param keyField Field for use as unique key for tranSMART mapping file.
    * @param visitField Field to be used as visit field in tranSMART mapping file.
    * @param fieldCategoryMap Define which field names map to which tranSMART categories.
    * @param rootCategory Category to be used as tranSMART root.
    * @param fieldLabelMap (Re)map field to labels in tranSMART file.
    * @return
    */
  def createMappingFile(
    delimiter : String,
    newLine : String,
    replacements : Iterable[(String, String)]
  )(
    items : Traversable[JsObject],
    dataFileName : String,
    keyField : String,
    visitField : Option[String],
    fieldCategoryMap : Map[String, Category],
    rootCategory : Category,
    fieldLabelMap : Map[String, String]
  ) : String

}

@Singleton
class TranSMARTServiceImpl extends TranSMARTService {

  override def createClinicalData(
    items : Traversable[JsObject],
    fieldsToInclude : Option[List[String]],
    fieldsToExclude : Option[List[String]]
  ) = {
    if (fieldsToInclude.isDefined && fieldsToExclude.isDefined)
      throw new IllegalArgumentException("'Fields to include' and 'fields to exclude' cannot be defined at the same time.")

    if (fieldsToInclude.isDefined) {
      def filterFields(field : String, value : JsValue) = fieldsToInclude.get.contains(field)
      items.map(filterJson(filterFields))
    } else if (fieldsToExclude.isDefined) {
      def filterFields(field : String, value : JsValue) = !fieldsToExclude.get.contains(field)
      items.map(filterJson(filterFields))
    } else
      items
  }

  /**
    * Filters the given JsObject with a condition function.
    * TODO: This belongs into a utility object/ singleton.
    *
    * @param condition Filter function. Takes a string identifying an entry of the JsValue. Could be a e.g. a test for elements.
    * @param item JsObject to be filtered.
    * @return JsObject with entriies filtered by condition function.
    */
  private def filterJson(condition : (String, JsValue) => Boolean)(item : JsObject) = {
    val filteredFields = item.fields.filter{case (field, value) => condition(field, value)}
    JsObject(filteredFields)
  }

  override def createClinicalMapping(
    dataFileName : String,
    keyField : String,
    visitField : Option[String],
    fieldsInOrder : Iterable[String],
    fieldCategoryMap : Map[String, Category],
    rootCategory : Category,
    fieldLabelMap : Map[String, String]
   ) = {
    fieldsInOrder.zipWithIndex.map{ case (fieldName, index) =>
      val (label, path) = if (fieldName.equals(keyField))
        (JsString("SUBJ_ID"), None)
      else if (visitField.isDefined && visitField.get.equals(fieldName))
        (JsString("VISIT_ID"), None)
      else {
        val label = JsString(toHumanReadableCamel(fieldLabelMap.getOrElse(fieldName, fieldName)))
        val path = fieldCategoryMap.get(fieldName).map(_.getPath.mkString("+").replaceAll(" ", "_"))
        (label, path)
      }

      JsObject(
        List(
          ("filename", JsString(dataFileName)),
          ("category_cd", if (path.isDefined) JsString(path.get) else JsNull),
          ("col_nbr", Json.toJson(index + 1)),
          ("data_label", label)
        )
      )
    }
  }

  override def createClinicalDataAndMappingFiles(
    delimiter : String,
    newLine : String,
    replacements : Iterable[(String, String)]
  )(
    items : Traversable[JsObject],
    dataFileName : String,
    keyField : String,
    visitField : Option[String],
    fieldCategoryMap : Map[String, Category],
    rootCategory : Category,
    fieldLabelMap : Map[String, String]
  ) = {
    val fieldsToInclude = (if (visitField.isDefined) List(keyField, visitField.get) else List(keyField)) ++
      fieldCategoryMap.keys.filterNot(_.equals(keyField)).toList

    val clinicalData = createClinicalData(items, Some(fieldsToInclude), None)
    if (clinicalData.nonEmpty) {
      val fieldsInOrder = clinicalData.head.fields.map(_._1).filter(fieldsToInclude.contains)
      val mappingData = createClinicalMapping(dataFileName, keyField, visitField, fieldsInOrder, fieldCategoryMap, rootCategory, fieldLabelMap)

      val dataContent = jsonObjectsToCsv(delimiter, newLine, None, replacements)(clinicalData)
      val mappingContent = jsonObjectsToCsv(delimiter, newLine, None, replacements)(mappingData)
      (dataContent, mappingContent)
    } else
      ("" , "")
  }


  def createClinicalDataFile(
    delimiter : String,
    newLine : String,
    replacements : Iterable[(String, String)]
  )(
    items : Traversable[JsObject],
    keyField : String,
    visitField : Option[String],
    fieldCategoryMap : Map[String, Category]
  )  = {
    val fieldsToInclude = (if (visitField.isDefined) List(keyField, visitField.get) else List(keyField)) ++
      fieldCategoryMap.map{case (field, category) => escapeKey(field)}.filterNot(_.equals(keyField)).toList

    val clinicalData = createClinicalData(items, Some(fieldsToInclude), None)
    if (clinicalData.nonEmpty) {
      val dataContent = jsonObjectsToCsv(delimiter, newLine, None, replacements)(clinicalData)
      dataContent
    } else
      ""
  }


  def createMappingFile(
    delimiter : String,
    newLine : String,
    replacements : Iterable[(String, String)]
  )(
    items : Traversable[JsObject],
    dataFileName : String,
    keyField : String,
    visitField : Option[String],
    fieldCategoryMap : Map[String, Category],
    rootCategory : Category,
    fieldLabelMap : Map[String, String]
   ) = {
    val fieldsToInclude = (if (visitField.isDefined) List(keyField, visitField.get) else List(keyField)) ++
      fieldCategoryMap.map{case (field, category) => escapeKey(field)}.filterNot(_.equals(keyField)).toList

    val clinicalData = createClinicalData(items, Some(fieldsToInclude), None)
    if (clinicalData.nonEmpty) {
      val fieldsInOrder = clinicalData.head.fields.map(_._1).filter(fieldsToInclude.contains)
      val mappingData = createClinicalMapping(dataFileName, keyField, visitField, fieldsInOrder, fieldCategoryMap, rootCategory, fieldLabelMap)

      val mappingContent = jsonObjectsToCsv(delimiter, newLine, None, replacements)(mappingData)
      mappingContent
    } else
      ""
  }
}
