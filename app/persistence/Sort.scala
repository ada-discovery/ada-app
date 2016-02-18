package persistence

class Sort(fieldName : String)
case class AscSort(fieldName : String) extends Sort(fieldName)
case class DescSort(fieldName : String) extends Sort(fieldName)