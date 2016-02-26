package models

import java.util.UUID
import java.util.Date

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

case class DataSetMetaInfo(
  _id: Option[BSONObjectID],
  id: String,
  name: String,
  timeCreated: Date
)

case class Dictionary(
  _id: Option[BSONObjectID],
  dataSetName: String,
  fields: Seq[Field]
//  parents : Seq[Dictionary],
)

case class Field(
  name : String,
  fieldType : FieldType.Value,
  isArray : Boolean = false,
  numValues : Option[Map[String, String]] = None,
  aliases : Seq[String] = Seq[String](),
  label : Option[String] = None,
  categoryId: Option[BSONObjectID] = None,
  var category : Option[Category] = None
)

case class NumFieldStats(min : Double, max : Double, mean : Double, variance : Double)

object FieldType extends Enumeration {
  val Null, Boolean, Double, Integer, Enum, String, Date = Value
}

case class Category(
  _id : Option[BSONObjectID],
  name : String,
  var parent : Option[Category] = None,
  var children : Seq[Category] = Seq[Category]()
) {
  def this(name : String) = this(None, name)

  def getPath : Seq[String] = (if (parent.isDefined && parent.get.parent.isDefined) parent.get.getPath else Seq[String]()) ++ Seq(name)

  def addChildren(newChildren  : Seq[Category]) : Category = {
    children = newChildren
    children.foreach(_.parent = Some(this))
    this
  }

  override def toString = name

  override def hashCode = name.hashCode
}

// JSON converters and identities

object Dictionary {
  implicit val enumTypeFormat = EnumFormat.enumFormat(FieldType)
  implicit val CategoryFormat = Json.format[Category]
  implicit val FieldFormat = Json.format[Field]
  implicit val DictionaryFormat = Json.format[Dictionary]

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
}

// TODO:
//trait BSONIdentifiable {
//
//}

// def union(refSet : Dataset, anotherset : Dataset)