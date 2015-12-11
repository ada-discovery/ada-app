package models

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

case class Dictionary(
  _id : Option[BSONObjectID],
  dataSetName : String,
  fields : List[Field]
//  parents : List[Dictionary],
)

case class Field(
  name : String,
  fieldType : FieldType.Value = null,
  numValues : Option[List[String]] = None,
  isArray : Boolean = false,
//  aliases : List[String] = List[String](),
  labels : List[String] = List[String]()
//  category : Category
) {
  override def equals(o : Any) = o match {
    case field: Field => this.name.equals(field.name)
    case _ => false
  }
}

case class NumFieldStats(min : Double, max : Double, mean : Double, variance : Double)

object FieldType extends Enumeration {
  val Null, Boolean, Double, Integer, String, Date = Value
}

case class Category(
  _id : Option[BSONObjectID],
  name : String,
  var parent : Option[Category] = None,
  var children : List[Category] = List[Category]()
) {

  def this(name : String) = this(None, name)

  def getPath : List[String] = (if (parent.isDefined && parent.get.parent.isDefined) parent.get.getPath else List[String]()) ++ List(name)

  def addChildren(newChildren  : List[Category]) : Category = {
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
  implicit val FieldFormat = Json.format[Field]
  implicit val DictionaryFormat = Json.format[Dictionary]

  implicit object DictionaryIdentity extends BSONObjectIdentity[Dictionary] {
    def of(entity: Dictionary): Option[BSONObjectID] = entity._id
    protected def set(entity: Dictionary, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}

object Category {
  implicit val CategoryFormat = Json.format[Category]

  implicit object CategoryIdentity extends BSONObjectIdentity[Category] {
    def of(entity: Category): Option[BSONObjectID] = entity._id
    protected def set(entity: Category, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}

// TODO:
//trait BSONIdentifiable {
//
//}

//case class TypeStats {
//  enum : Boolean,
//  min :
//}

// def union(refSet : Dataset, anotherset : Dataset)