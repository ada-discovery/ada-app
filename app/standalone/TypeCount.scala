package standalone

class TypeCount (
    var nullOrNa : Int = 0,
    var date : Int = 0,
    var boolean : Int = 0,
    var numberEnum : Int = 0,
    var freeNumber : Int = 0,
    var textEnum : Int = 0,
    var freeText : Int = 0
  ) {
    override def toString() : String = s"null-or-NAs : $nullOrNa\nbooleans : $boolean\ndates: $date\nnumber enums: $numberEnum\nfree numbers : $freeNumber\ntext enums : $textEnum\nfree texts : $freeText"
}
