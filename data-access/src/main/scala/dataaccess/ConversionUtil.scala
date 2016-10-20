package dataaccess

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import scala.reflect.ClassTag

object ConversionUtil {

  def toDouble = convert(_.toDouble)_

  def toInt = convert(_.toInt)_

  def toLong = convert(_.toLong)_

  def toFloat = convert(_.toFloat)_

  def toBoolean = convert(toBooleanAux)_

//  def toBoolean(valueMap: Map[String, Boolean]) = convert { text =>
//    valueMap.getOrElse(text.toLowerCase, typeExpectedException(text, classOf[Boolean]))
//  }_

  def toDate(dateFormats: Traversable[String]) = convert(toDateAux(dateFormats))_

  private def toDateAux(dateFormats: Traversable[String])(text: String) = {
    val dates = dateFormats.map { format =>
      try {
        val date = new SimpleDateFormat(format).parse(text)
        val year1900 = date.getYear
        // we assume that a valid year is between 1900 and 2100
        if (year1900 > 0 && year1900 < 200)
          Some(date)
        else
          None
      } catch {
        case e: ParseException => None
      }
    }.flatten

    dates.headOption.getOrElse(
      throw typeExpectedException(text, classOf[Date])
    )
  }

  private def toBooleanAux(text: String) = {
    try {
      text.toBoolean
    } catch {
      case e: IllegalArgumentException =>
          text match {
            case "0" => false
            case "1" => true
            case _ => typeExpectedException(text, classOf[Boolean])
          }
    }
  }

  def isDouble = isConvertible(_.toDouble)_

  def isInt = isConvertible(_.toInt)_

  def isLong = isConvertible(_.toLong)_

  def isFloat = isConvertible(_.toFloat)_

  def isBoolean = isConvertible(toBooleanAux)_

//  def isBoolean(valueMap: Map[String, Boolean]) = isConvertible { text =>
//    valueMap.getOrElse(text, typeExpectedException(text, classOf[Boolean]))
//  }_

  def isDate(dateFormats: Traversable[String]) = isConvertible(toDateAux(dateFormats))_

  private def convert[T](
    fun: String => T)(
    text: String)(
    implicit tag: ClassTag[T]
  ): T = try {
    fun(text.trim)
  } catch {
    case t: NumberFormatException => typeExpectedException(text, tag.runtimeClass)
    case t: IllegalArgumentException => typeExpectedException(text, tag.runtimeClass)
  }

  private def isConvertible[T](
    fun: String => T)(
    text: String)(
    implicit tag: ClassTag[T]
  ): Boolean = try {
    convert[T](fun)(text); true
  } catch {
    case t: AdaConversionException => false
  }

  def typeExpectedException(value: String, expectedType: Class[_]) =
    throw new AdaConversionException(s"String $value is expected to be ${expectedType.getSimpleName}-convertible but it's not.")
}

class AdaConversionException(message: String) extends RuntimeException(message)