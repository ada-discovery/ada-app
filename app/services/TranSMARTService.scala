package services

import javax.inject.{Inject, Singleton}

import _root_.util.JsonUtil.{jsonObjectsToCsv, escapeKey, unescapeKey}
import com.google.inject.ImplementedBy
import play.api.libs.json._
import models.Category

@ImplementedBy(classOf[TranSMARTServiceImpl])
trait TranSMARTService {

  def createClinicalData(
    items : Traversable[JsObject],
    fieldsToInclude : Option[List[String]],
    fieldsToExclude : Option[List[String]]
  ) : Traversable[JsObject]

  def createClinicalMapping(
    dataFileName : String,
    keyField : String,
    visitField : Option[String],
    fieldsInOrder : Iterable[String],
    fieldCategoryMap : Map[String, Category],
    rootCategory : Category,
    fieldLabelMap : Map[String, String]
  ) : Traversable[JsObject]

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
    fieldsInOrder.zipWithIndex.map{ case (field, index) =>
      val fieldName = unescapeKey(field)

      val (label, path) = if (field.equals(keyField))
        (JsString("SUBJ_ID"), None)
      else if (visitField.isDefined && visitField.get.equals(field))
        (JsString("VISIT_ID"), None)
      else {
        val label = JsString(toCamel(fieldLabelMap.getOrElse(fieldName, fieldName)))
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
      fieldCategoryMap.map{case (field, category) => escapeKey(field)}.filterNot(_.equals(keyField)).toList

    val clinicalData = createClinicalData(items, Some(fieldsToInclude), None)
    if (clinicalData.nonEmpty) {
      val fieldsInOrder = clinicalData.head.fields.map(_._1).filter(fieldsToInclude.contains)
      val mappingData = createClinicalMapping(dataFileName, keyField, visitField, fieldsInOrder, fieldCategoryMap, rootCategory, fieldLabelMap)

      val dataContent = jsonObjectsToCsv(delimiter, newLine, replacements)(clinicalData)
      val mappingContent = jsonObjectsToCsv(delimiter, newLine, replacements)(mappingData)
      (dataContent, mappingContent)
    } else
      ("" , "")
  }

  private def toCamel(s: String): String = {
    val split = s.split("_")
    split.map { x => x.capitalize}.mkString(" ")
  }
}
