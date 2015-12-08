package models

import reactivemongo.bson.BSONObjectID

case class Dictionary(
  _id : Option[BSONObjectID],
  dataSetName : String,
  fields : Traversable[Field]
  //  parents : List[Dictionary],
)

object FieldType extends Enumeration {
  val Null, Boolean, Double, Integer, String, Date = Value
}

case class Field(
  _id : Option[BSONObjectID],
  name : String,
  fieldType : FieldType.Value,
  isArray : Boolean,
  aliases : List[String],
  labels : List[String],
  category : Category
)

case class Category(
    name : String,
    var parent : Option[Category] = None,
    var children : Iterable[Category] = List[Category]()
  ) {

  def getPath : List[String] = (if (parent.isDefined && parent.get.parent.isDefined) parent.get.getPath else List[String]()) ++ List(name)

  def addChildren(newChildren  : Iterable[Category]) : Category = {
    children = newChildren
    children.foreach(_.parent = Some(this))
    this
  }

  override def toString = name

  override def hashCode = name.hashCode
}


//case class TypeStats {
//  enum : Boolean,
//  min :
//}

// def union(refSet : Dataset, anotherset : Dataset)