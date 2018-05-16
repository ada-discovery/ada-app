package services.stats.calcjsonin

import models.{Field, FieldTypeId}
import play.api.libs.json.JsObject
import services.stats.calc.JsonFieldUtil._
import services.stats.calc._

import scala.reflect.runtime.universe._

////////////////////////
// General Converters //
////////////////////////

class AnyScalarConverter extends ScalarConverter[Any]

class AnyArrayConverter extends ArrayConverter[Any]
class IntArrayConverter extends ArrayConverter[Int]
class DoubleArrayConverter extends ArrayConverter[Double]
class LongArrayConverter extends ArrayConverter[Long]
class DateArrayConverter extends ArrayConverter[java.util.Date]
class StringArrayConverter extends ArrayConverter[String]
class BooleanArrayConverter extends ArrayConverter[Boolean]

class AnyTupleConverter extends TupleConverter[Any, Any]
class AnyArrayTupleConverter extends ArrayTupleConverter[Any, Any]

////////////////////////////////////
// Calculator Specific Converters //
////////////////////////////////////

class ScalarNumericDistributionCountsConverter extends ScalarDoubleConverter {
  override def specificUseClass = Some(NumericDistributionCountsCalcAux.getClass)
}

class ArrayNumericDistributionCountsConverter extends ArrayDoubleConverter {
  override def specificUseClass = Some(NumericDistributionCountsCalcAux.getClass)
}

class ScalarGroupNumericDistributionCountsConverter extends ScalarGroupDoubleConverter[Any] {
  override def specificUseClass = Some(classOf[GroupNumericDistributionCountsCalc[Any]])
}

class ArrayGroupNumericDistributionCountsConverter extends ArrayGroupDoubleConverter[Any] {
  override def specificUseClass = Some(classOf[GroupNumericDistributionCountsCalc[Any]])
}

class ScalarCumulativeNumericBinCountsConverter extends ScalarDoubleConverter {
  override def specificUseClass = Some(classOf[CumulativeNumericBinCountsCalc])
}

class ArrayCumulativeNumericBinCountsConverter extends ArrayDoubleConverter {
  override def specificUseClass = Some(classOf[CumulativeNumericBinCountsCalc])
}

class ScalarGroupCumulativeNumericBinCountsConverter extends ScalarGroupDoubleConverter[Any] {
  override def specificUseClass = Some(classOf[GroupCumulativeNumericBinCountsCalc[Any]])
}

class ArrayGroupCumulativeNumericBinCountsConverter extends ArrayGroupDoubleConverter[Any] {
  override def specificUseClass = Some(classOf[GroupNumericDistributionCountsCalc[Any]])
}

class AnyStringGroupTupleConverter extends StringGroupTupleConverter[Any, Any] {
  override def specificUseClass = Some(classOf[GroupTupleCalc[String, _, _]])
}

class AnyStringUniqueGroupTupleConverter extends StringGroupTupleConverter[Any, Any] {
  override def specificUseClass = Some(classOf[GroupUniqueTupleCalc[String, _, _]])
}

class BasicStatsConverter extends ScalarDoubleConverter {
  override def specificUseClass = Some(BasicStatsCalc.getClass)
}

class ArrayBasicStatsConverter extends ArrayDoubleConverter {
  override def specificUseClass = Some(BasicStatsCalc.getClass)
}

class PearsonCorrelationConverter extends SeqDoubleConverter {
  override def specificUseClass = Some(PearsonCorrelationCalc.getClass)
}

class AllDefinedPearsonCorrelationConverter extends AllDefinedSeqDoubleConverter {
  override def specificUseClass = Some(AllDefinedPearsonCorrelationCalc.getClass)
}

class EuclideanDistanceCalcConverter extends SeqDoubleConverter {
  override def specificUseClass = Some(EuclideanDistanceCalc.getClass)
}

class AllDefinedEuclideanDistanceCalcConverter extends AllDefinedSeqDoubleConverter {
  override def specificUseClass = Some(AllDefinedEuclideanDistanceCalc.getClass)
}

class SeqBinMeanCalcConverter extends SeqDoubleConverter {
  override def specificUseClass = Some(SeqBinMeanCalcAux.getClass)
}

class AllDefinedSeqBinMeanCalcConverter extends AllDefinedSeqDoubleConverter {
  override def specificUseClass = Some(classOf[AllDefinedSeqBinMeanCalc])
}

////////////////////
// Helper Classes //
////////////////////

private[calcjsonin] abstract class ScalarConverter[T: TypeTag] extends JsonInputConverter[Option[T]] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 1)
    jsonToValue[T](fields(0))
  }

  override def inputType = typeOf[Option[T]]
}

private[calcjsonin] abstract class ScalarDoubleConverter extends JsonInputConverter[Option[Double]] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 1)
    jsonToDouble(fields(0))
  }

  override def inputType = typeOf[Option[Double]]
}

private[calcjsonin] abstract class ArrayConverter[T: TypeTag] extends JsonInputConverter[Array[Option[T]]] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 1)
    jsonToArrayValue[T](fields(0))
  }

  override def inputType = typeOf[Array[Option[T]]]
}

private[calcjsonin] abstract class ArrayDoubleConverter extends JsonInputConverter[Array[Option[Double]]] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 1)
    jsonToArrayDouble(fields(0))
  }

  override def inputType = typeOf[Array[Option[Double]]]
}

private[calcjsonin] abstract class SeqDoubleConverter extends JsonInputConverter[Seq[Option[Double]]] {

  override def apply(fields: Seq[Field]) =
    jsonToDoubles(fields)

  override def inputType = typeOf[Seq[Option[Double]]]
}

private[calcjsonin] abstract class AllDefinedSeqDoubleConverter extends JsonInputConverter[Seq[Double]] {

  override def apply(fields: Seq[Field]) =
    jsonToDoublesDefined(fields)

  override def inputType = typeOf[Seq[Double]]
}

private[calcjsonin] abstract class TupleConverter[T1: TypeTag, T2: TypeTag] extends JsonInputConverter[(Option[T1], Option[T2])] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 2)
    jsonToTuple[T1, T2](fields(0), fields(1))
  }

  override def inputType = typeOf[(Option[T1], Option[T2])]
}

private[calcjsonin] abstract class ScalarGroupDoubleConverter[G: TypeTag] extends JsonInputConverter[(Option[G], Option[Double])] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 2)
    val groupConverter = jsonToValue(fields(0))
    val doubleConverter = jsonToDouble(fields(1))
    (jsObject: JsObject) =>
      (groupConverter(jsObject), doubleConverter(jsObject))
  }

  override def inputType = typeOf[(Option[G], Option[Double])]
}

private[calcjsonin] abstract class ArrayTupleConverter[T1: TypeTag, T2: TypeTag] extends JsonInputConverter[Array[(Option[T1], Option[T2])]] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 2)

    val groupConverter = jsonToValue[T1](fields(0))
    val arrayValueConverter = jsonToArrayValue[T2](fields(1))

    (jsObject: JsObject) =>
      val group = groupConverter(jsObject)
      val array = arrayValueConverter(jsObject)
      array.map((group, _))
  }

  override def inputType = typeOf[Array[(Option[T1], Option[T2])]]
}

private[calcjsonin] abstract class ArrayGroupDoubleConverter[G: TypeTag] extends JsonInputConverter[Array[(Option[G], Option[Double])]] {

  override def apply(fields: Seq[Field]) = {
    val groupConverter = jsonToValue[G](fields(0))
    val doubleConverter = jsonToArrayDouble(fields(1))

    (jsObject: JsObject) =>
      val group = groupConverter(jsObject)
      val array = doubleConverter(jsObject)
      array.map((group, _))
  }

  override def inputType = typeOf[Array[(Option[G], Option[Double])]]
}

private[calcjsonin] abstract class ScalarNumericConverter[T: TypeTag] extends JsonInputConverter[Option[T]] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 1)

    val converter = jsonToValue[T](fields(0))

    fields(0).fieldType match {
      case FieldTypeId.Date =>
        (jsObject: JsObject) =>
          converter(jsObject).map(
            _.asInstanceOf[java.util.Date].getTime.asInstanceOf[T]
          )

      case _ =>
        converter
    }
  }

  override def inputType = typeOf[Option[T]]
}

private[calcjsonin] abstract class ArrayNumericConverter[T: TypeTag] extends JsonInputConverter[Array[Option[T]]] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 1)

    val converter = jsonToArrayValue[T](fields(0))

    fields(0).fieldType match {
      case FieldTypeId.Date =>
        (jsObject: JsObject) =>
          converter(jsObject).map(_.map(
            _.asInstanceOf[java.util.Date].getTime.asInstanceOf[T])
          )

      case _ =>
        converter
    }
  }

  override def inputType = typeOf[Array[Option[T]]]
}

private[calcjsonin] abstract class GroupScalarNumericConverter[G: TypeTag, T: TypeTag] extends JsonInputConverter[(Option[G], Option[T])] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 2)

    val groupConverter = jsonToValue[G](fields(0))
    val converter = jsonToValue[T](fields(1))

    val valueConverter = fields(1).fieldType match {
      case FieldTypeId.Date =>
        (jsObject: JsObject) =>
          converter(jsObject).map(
            _.asInstanceOf[java.util.Date].getTime.asInstanceOf[T]
          )

      case _ =>
        converter
    }

    (jsObject: JsObject) =>
      val group = groupConverter(jsObject)
      val value = valueConverter(jsObject)
      (group, value)
  }

  override def inputType = typeOf[(Option[G], Option[T])]
}

private[calcjsonin] abstract class ArrayGroupNumericConverter[G: TypeTag, T: TypeTag] extends JsonInputConverter[Array[(Option[G], Option[T])]] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 2)

    val groupConverter = jsonToValue[G](fields(0))
    val converter = jsonToArrayValue[T](fields(1))

    val arrayValueConverter = fields(1).fieldType match {
      case FieldTypeId.Date =>
        (jsObject: JsObject) =>
          converter(jsObject).map(_.map(
            _.asInstanceOf[java.util.Date].getTime.asInstanceOf[T])
          )

      case _ =>
        converter
    }

    (jsObject: JsObject) =>
      val group = groupConverter(jsObject)
      val array = arrayValueConverter(jsObject)
      array.map((group, _))
  }

  override def inputType = typeOf[Array[(Option[G], Option[T])]]
}

private[calcjsonin] abstract class StringGroupTupleConverter[T1: TypeTag, T2: TypeTag] extends JsonInputConverter[GroupTupleCalcTypePack[String, T1, T2]#IN] {

  override def apply(fields: Seq[Field]) = {
    requireFields(fields, 3)

    // json to tuple converter
    val jsonTuple = jsonToTuple[T1, T2](fields(1), fields(2))

    // create a group->string converter and merge with the value one
    val groupJsonString = jsonToDisplayString(fields(0))

    (jsObject: JsObject) =>
      val values = jsonTuple(jsObject)
      (groupJsonString(jsObject), values._1, values._2)
  }

  override def inputType = typeOf[(Option[String], Option[T1], Option[T2])]
}