package dataaccess

abstract class Sort{
  val fieldName : String
}
case class AscSort(fieldName : String) extends Sort
case class DescSort(fieldName : String) extends Sort