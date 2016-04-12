package models

import java.util.UUID
import java.util.Date

import com.fasterxml.jackson.annotation.JsonIgnore
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import play.api.libs.functional.syntax._

case class DataSpaceMetaInfo(
  _id: Option[BSONObjectID],
  name: String,
  timeCreated: Date = new Date()
)

case class DataSetMetaInfo(
  _id: Option[BSONObjectID],
  id: String,
  name: String,
  dataSpaceId: Option[BSONObjectID],
  timeCreated: Date = new Date(),
  sourceDataSetId: Option[BSONObjectID] = None
)

case class DataSetUISetting(
  _id: Option[BSONObjectID],
  dataSetId: Option[BSONObjectID]
)

case class Dictionary(
  _id: Option[BSONObjectID],
  dataSetId: String,
  fields: Seq[Field],
  categories: Seq[Category]
//  parents : Seq[Dictionary],
)

case class Field(
  name : String,
  fieldType : FieldType.Value,
  isArray : Boolean = false,
  numValues : Option[Map[String, String]] = None,
  aliases : Seq[String] = Seq[String](),
  label : Option[String] = None,
  var categoryId: Option[BSONObjectID] = None,
  var category : Option[Category] = None
)

case class NumFieldStats(min : Double, max : Double, mean : Double, variance : Double)

object FieldType extends Enumeration {
  val Null, Boolean, Double, Integer, Enum, String, Date = Value
}

case class Category(
  _id : Option[BSONObjectID],
  name : String,
  var parentId : Option[BSONObjectID] = None,
  var parent : Option[Category] = None,
  var children : Seq[Category] = Seq[Category]()
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
  implicit val enumTypeFormat = EnumFormat.enumFormat(FieldType)
  implicit val categoryFormat: Format[Category] = (
    (__ \ "_id").formatNullable[BSONObjectID] and
    (__ \ "name").format[String] and
    (__ \ "parentId").formatNullable[BSONObjectID]
    )(Category(_, _, _), (item: Category) =>  (item._id, item.name, item.parentId))

  implicit val fieldFormat: Format[Field] = (
    (__ \ "name").format[String] and
    (__ \ "fieldType").format[FieldType.Value] and
    (__ \ "isArray").format[Boolean] and
    (__ \ "numValues").formatNullable[Map[String, String]] and
    (__ \ "aliases").format[Seq[String]] and
    (__ \ "label").formatNullable[String] and
    (__ \ "categoryId").formatNullable[BSONObjectID]
  )(
    Field(_, _, _, _, _, _, _),
    (item: Field) =>  (item.name, item.fieldType, item.isArray, item.numValues, item.aliases, item.label, item.categoryId)
  )

  implicit val dictionaryFormat = Json.format[Dictionary]
  implicit val dataSetMetaInfoFormat = Json.format[DataSetMetaInfo]

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
}

object DataSetId {
  val luxpark = "luxpark"
  val denopa_curated_baseline = "denopa_curated_baseline"
  val denopa_curated_firstvisit = "denopa_curated_firstvisit"
  val denopa_curated_secondvisit = "denopa_curated_secondvisit"
  val denopa_baseline = "denopa_baseline"
  val denopa_firstvisit = "denopa_firstvisit"
  val denopa_secondvisit = "denopa_secondvisit"
}

// TODO:
//trait BSONIdentifiable {
//
//}

// def union(refSet : Dataset, anotherset : Dataset)