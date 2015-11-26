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




//case class TypeStats {
//  enum : Boolean,
//  min :
//}

// def union(refSet : Dataset, anotherset : Dataset)