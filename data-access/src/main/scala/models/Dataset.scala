package models

import java.util.{Date, UUID}

import dataaccess.{ManifestedFormat, SubTypeFormat}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import dataaccess._
import models.FilterCondition.filterFormat
import models.DataView.dataViewFormat

case class DataSpaceMetaInfo(
  _id: Option[BSONObjectID],
  name: String,
  sortOrder: Int,
  timeCreated: Date = new Date(),
  dataSetMetaInfos: Seq[DataSetMetaInfo] = Seq[DataSetMetaInfo]()
)

case class DataSetMetaInfo(
  _id: Option[BSONObjectID],
  id: String,
  name: String,
  sortOrder: Int,
  hide: Boolean,
  dataSpaceId: Option[BSONObjectID] = None,
  timeCreated: Date = new Date(),
  sourceDataSetId: Option[BSONObjectID] = None
)

case class DataSetSetting(
  _id: Option[BSONObjectID],
  dataSetId: String,
  keyFieldName: String,
  exportOrderByFieldName: Option[String],
  listViewTableColumnNames: Seq[String],
  statsCalcSpecs: Seq[StatsCalcSpec],
  overviewChartElementGridWidth: Int,
  defaultScatterXFieldName: String,
  defaultScatterYFieldName: String,
  defaultDistributionFieldName: String,
  defaultDateCountFieldName: String,
  filterShowFieldStyle: Option[FilterShowFieldStyle.Value],
  tranSMARTVisitFieldName: Option[String],
  tranSMARTReplacements: Map[String, String],
  cacheDataSet: Boolean = false
) {
  def this(dataSetId: String) = this(None, dataSetId, "", None, Nil, Nil, 3, "", "", "", "", None, None, Map[String, String]())
}

object DataSetSetting {
  def apply2(
    _id: Option[BSONObjectID],
    dataSetId: String,
    keyFieldName: String,
    exportOrderByFieldName: Option[String],
    listViewTableColumnNames: Seq[String],
    distributionChartFieldNames: Seq[String],
    overviewChartElementGridWidth: Int,
    defaultScatterXFieldName: String,
    defaultScatterYFieldName: String,
    defaultDistributionFieldName: String,
    defaultDateCountFieldName: String,
    filterShowFieldStyle: Option[FilterShowFieldStyle.Value],
    tranSMARTVisitFieldName: Option[String],
    tranSMARTReplacements: Map[String, String],
    cacheDataSet: Boolean
  ) = DataSetSetting(
    _id, dataSetId, keyFieldName, exportOrderByFieldName,
    listViewTableColumnNames, distributionChartFieldNames.map(DistributionCalcSpec(_, None)), overviewChartElementGridWidth,
    defaultScatterXFieldName, defaultScatterYFieldName, defaultDistributionFieldName, defaultDateCountFieldName,
    filterShowFieldStyle, tranSMARTVisitFieldName, tranSMARTReplacements,
    cacheDataSet
  )
}

@Deprecated
case class FieldChartType(
  fieldName: String,
  chartType: Option[ChartType.Value]
)

abstract class StatsCalcSpec {
  def fieldNames: Traversable[String]
  def outputGridWidth: Option[Int]
}

case class DistributionCalcSpec(
  fieldName: String,
  chartType: Option[ChartType.Value],
  outputGridWidth: Option[Int] = None
) extends StatsCalcSpec {
  override val fieldNames = Seq(fieldName)
}

case class BoxCalcSpec(
  fieldName: String,
  outputGridWidth: Option[Int] = None
) extends StatsCalcSpec {
  override val fieldNames = Seq(fieldName)
}

case class ScatterCalcSpec(
  xFieldName: String,
  yFieldName: String,
  groupFieldName: Option[String],
  outputGridWidth: Option[Int] = None
) extends StatsCalcSpec {
  override val fieldNames = Seq(Some(xFieldName), Some(yFieldName), groupFieldName).flatten
}

case class CorrelationCalcSpec(
  fieldNames: Seq[String],
  outputGridWidth: Option[Int] = None
) extends StatsCalcSpec

object ChartType extends Enumeration {
  val Pie, Column, Bar, Line, Polar = Value
}

case class Dictionary(
  _id: Option[BSONObjectID],
  dataSetId: String,
  fields: Seq[Field],
  categories: Seq[Category],
  filters: Seq[Filter],
  dataviews: Seq[DataView]
//  parents : Seq[Dictionary],
)

case class Field(
  name: String,
  label: Option[String] = None,
  fieldType: FieldTypeId.Value = FieldTypeId.String,
  isArray: Boolean = false,
  numValues: Option[Map[String, String]] = None, // TODO: rename to enumValues
  aliases: Seq[String] = Seq[String](),
  var categoryId: Option[BSONObjectID] = None,
  var category: Option[Category] = None
) {
  def fieldTypeSpec: FieldTypeSpec =
    FieldTypeSpec(fieldType, isArray, numValues.map(_.map{ case (a,b) => (a.toInt, b)}))

  def labelOrElseName = label.getOrElse(name)
}

object FieldTypeId extends Enumeration {
  val Null, Boolean, Double, Integer, Enum, String, Date, Json = Value
}

case class FieldTypeSpec(
  fieldType: FieldTypeId.Value,
  isArray: Boolean = false,
  enumValues: Option[Map[Int, String]] = None
)

case class NumFieldStats(min : Double, max : Double, mean : Double, variance : Double)

case class Category(
  _id: Option[BSONObjectID],
  name: String,
  var parentId: Option[BSONObjectID] = None,
  var parent: Option[Category] = None,
  var children: Seq[Category] = Seq[Category]()
) {
  def this(name : String) = this(None, name)

  def getPath : Seq[String] = (if (parent.isDefined && parent.get.parent.isDefined) parent.get.getPath else Seq[String]()) ++ Seq(name)

  def setChildren(newChildren: Seq[Category]): Category = {
    children = newChildren
    children.foreach(_.parent = Some(this))
    this
  }

  def addChild(newChild: Category): Category = {
    children = Seq(newChild) ++ children
    newChild.parent = Some(this)
    this
  }

  override def toString = name

  override def hashCode = name.hashCode
}

// JSON converters and identities

object DataSetFormattersAndIds {
  implicit val enumTypeFormat = EnumFormat.enumFormat(FieldTypeId)
  implicit val categoryFormat: Format[Category] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").format[String] and
    (__ \ "parentId").formatNullable[BSONObjectID]
    )(Category(_, _, _), (item: Category) =>  (item._id, item.name, item.parentId))

  implicit val fieldFormat: Format[Field] = (
    (__ \ "name").format[String] and
    (__ \ "label").formatNullable[String] and
    (__ \ "fieldType").format[FieldTypeId.Value] and
    (__ \ "isArray").format[Boolean] and
    (__ \ "numValues").formatNullable[Map[String, String]] and
    (__ \ "aliases").format[Seq[String]] and
    (__ \ "categoryId").formatNullable[BSONObjectID]
  )(
    Field(_, _, _, _, _, _, _),
    (item: Field) =>  (item.name, item.label, item.fieldType, item.isArray, item.numValues, item.aliases, item.categoryId)
  )

  implicit val chartEnumTypeFormat = EnumFormat.enumFormat(ChartType)
  implicit val fieldChartTypeFormat = Json.format[FieldChartType]

  implicit val statsCalcSpecFormat: Format[StatsCalcSpec] = new SubTypeFormat[StatsCalcSpec](
    Seq(
      ManifestedFormat(Json.format[DistributionCalcSpec]),
      ManifestedFormat(Json.format[BoxCalcSpec]),
      ManifestedFormat(Json.format[ScatterCalcSpec]),
      ManifestedFormat(Json.format[CorrelationCalcSpec])
    )
  )

  implicit val dictionaryFormat = Json.format[Dictionary]
  implicit val dataSetMetaInfoFormat = Json.format[DataSetMetaInfo]
  implicit val dataSpaceMetaInfoFormat = Json.format[DataSpaceMetaInfo]

  implicit val filterShowFieldStyleFormat = EnumFormat.enumFormat(FilterShowFieldStyle)
  val dataSetSettingFormat = Json.format[DataSetSetting]
  implicit val serializableDataSetSettingFormat = new SerializableFormat(dataSetSettingFormat.reads, dataSetSettingFormat.writes)
  val serializableBSONObjectIDFormat = new SerializableFormat(BSONObjectIDFormat.reads, BSONObjectIDFormat.writes)

  implicit object DictionaryIdentity extends BSONObjectIdentity[Dictionary] {
    def of(entity: Dictionary): Option[BSONObjectID] = entity._id
    protected def set(entity: Dictionary, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object FieldIdentity extends Identity[Field, String] {
    override val name = "name"
    override def next = UUID.randomUUID().toString
    override def set(entity: Field, name: Option[String]): Field = entity.copy(name = name.getOrElse(""))
    override def of(entity: Field): Option[String] = Some(entity.name)
  }

  implicit object CategoryIdentity extends BSONObjectIdentity[Category] {
    def of(entity: Category): Option[BSONObjectID] = entity._id
    protected def set(entity: Category, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object DataSetMetaInfoIdentity extends BSONObjectIdentity[DataSetMetaInfo] {
    def of(entity: DataSetMetaInfo): Option[BSONObjectID] = entity._id
    protected def set(entity: DataSetMetaInfo, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object DataSpaceMetaInfoIdentity extends BSONObjectIdentity[DataSpaceMetaInfo] {
    def of(entity: DataSpaceMetaInfo): Option[BSONObjectID] = entity._id
    protected def set(entity: DataSpaceMetaInfo, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object DataSetSettingIdentity extends BSONObjectIdentity[DataSetSetting] {
    def of(entity: DataSetSetting): Option[BSONObjectID] = entity._id
    protected def set(entity: DataSetSetting, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object JsObjectIdentity extends BSONObjectIdentity[JsObject] {
    override def of(json: JsObject): Option[BSONObjectID] =
      (json \ name).asOpt[BSONObjectID]

    override protected def set(json: JsObject, id: Option[BSONObjectID]): JsObject =
      json.+(name, Json.toJson(id))
  }
}