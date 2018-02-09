package services.stats.calc

import dataaccess.{AdaConversionException, FieldType, FieldTypeFactory}
import models.{Field, FieldTypeId}
import play.api.libs.json.{JsObject, JsReadable, Json}
import java.{util => ju}

import scala.collection.mutable.{Map => MMap}

object JsonFieldUtil {

  def jsonToDoubles(
    fields: Seq[Field]
  )(
    implicit ftf: FieldTypeFactory
  ): JsObject => Seq[Option[Double]] = {
    val valueGets: Seq[JsObject => Option[Double]] = doubleGets(fields)
    jsonToDoublesAux(valueGets)
  }

  def jsonToDoublesDefined(
    fields: Seq[Field]
  )(
    implicit ftf: FieldTypeFactory
  ): JsObject => Seq[Double] = {
    val valueGets: Seq[JsObject => Option[Double]] = doubleGets(fields)
    jsonToValuesDefinedAux(valueGets)
  }

  def jsonToValue[T](
    field: Field
  )(
    implicit ftf: FieldTypeFactory
  ): JsObject => Option[T] = {
    val fieldType = ftf(field.fieldTypeSpec)
    valueGet(fieldType.asValueOf[T], field.name)
  }

  def jsonToDisplayString[T](
    field: Field
  )(
    implicit ftf: FieldTypeFactory
  ): JsObject => Option[String] = {
    val fieldType = ftf(field.fieldTypeSpec)
    displayStringGet(fieldType.asValueOf[T], field.name)
  }

  def jsonToValues[T](
    fields: Seq[Field]
  )(
    implicit ftf: FieldTypeFactory
  ): JsObject => Seq[Option[T]] = {
    val valueGets = fields.map { field =>
      val fieldType = ftf(field.fieldTypeSpec)
      valueGet(fieldType.asValueOf[T], field.name)
    }
    jsonToDoublesAux(valueGets)
  }

  def jsonToTuple[T1, T2](
    field1: Field,
    field2: Field
  )(
    implicit ftf: FieldTypeFactory
  ): JsObject => (Option[T1], Option[T2]) = {
    val fieldType1 = ftf(field1.fieldTypeSpec)
    val value1Get = valueGet(fieldType1.asValueOf[T1], field1.name)

    val fieldType2 = ftf(field2.fieldTypeSpec)
    val value2Get = valueGet(fieldType2.asValueOf[T2], field2.name)

    { jsObject: JsObject => (value1Get(jsObject), value2Get(jsObject))}
  }

  def jsonToTuple[T1, T2, T3](
    field1: Field, field2: Field, field3: Field)(
    implicit ftf: FieldTypeFactory
  ): JsObject => (Option[T1], Option[T2], Option[T3]) = {

    def valGet[T](field: Field) = {
      val fieldType = ftf(field.fieldTypeSpec)
      valueGet(fieldType.asValueOf[T], field.name)
    }

    val value1Get = valGet[T1](field1)
    val value2Get = valGet[T2](field2)
    val value3Get = valGet[T3](field3)

    { jsObject: JsObject => (value1Get(jsObject), value2Get(jsObject), value3Get(jsObject))}
  }

  def jsonToArrayValue[T](
    field: Field)(
    implicit ftf: FieldTypeFactory
  ): JsObject => Array[Option[T]] = {
    val spec = field.fieldTypeSpec
    val fieldType = ftf(spec)

    if (spec.isArray) {
      val arrayFieldType = fieldType.asValueOf[Array[Option[T]]]
      valueGet(arrayFieldType, field.name).andThen( result =>
        result.getOrElse(Array.empty[Option[T]])
      )
    } else {
      val scalarFieldType = fieldType.asValueOf[T]
      valueGet(scalarFieldType, field.name).andThen( result =>
        Array(result)
      )
    }
  }

  def jsonsToValues[T](
    jsons: Traversable[JsReadable],
    fieldType: FieldType[_]
  ): Traversable[Option[T]] =
    if (fieldType.spec.isArray) {
      val typedFieldType = fieldType.asValueOf[Array[Option[T]]]

      jsons.map( json =>
        typedFieldType.jsonToValue(json).map(_.toSeq).getOrElse(Seq(None))
      ).flatten
    } else {
      val typedFieldType = fieldType.asValueOf[T]

      jsons.map(typedFieldType.jsonToValue)
    }

  private def doubleGets(
    fields: Seq[Field])(
    implicit ftf: FieldTypeFactory
  ):  Seq[JsObject => Option[Double]] = {
    val emptyDoubleValue = {_: JsObject => None}
    //    val specFieldTypeMap = MMap[FieldTypeSpec, FieldType[_]]()

    fields.map { field =>
      val spec = field.fieldTypeSpec
      val fieldType = ftf(spec)
      //      val fieldType = specFieldTypeMap.getOrElseUpdate(spec, ftf(spec))

      // helper function to create a getter for array head value
      def arrayHeadValueGet[T] = {
        val arrayFieldType = fieldType.asValueOf[Array[Option[T]]]
        valueGet(arrayFieldType, field.name).andThen(
          _.flatMap(_.headOption).flatten
        )
      }

      // helper function to create a getter for a (scalar) value
      def scalarValueGet[T] = valueGet(fieldType.asValueOf[T], field.name)

      if (spec.isArray) {
        field.fieldType match {
          case FieldTypeId.Double => arrayHeadValueGet[Double]
          case FieldTypeId.Integer => arrayHeadValueGet[Long].andThen(_.map(_.toDouble))
          case FieldTypeId.Date => arrayHeadValueGet[ju.Date].andThen(_.map(_.getTime.toDouble))
          case _ => emptyDoubleValue
        }
      } else {
        field.fieldType match {
          case FieldTypeId.Double => scalarValueGet[Double]
          case FieldTypeId.Integer => scalarValueGet[Long].andThen(_.map(_.toDouble))
          case FieldTypeId.Date => scalarValueGet[ju.Date].andThen(_.map(_.getTime.toDouble))
          case _ => emptyDoubleValue
        }
      }
    }
  }

  private def valueGet[T](fieldType: FieldType[T], fieldName: String) = {
    jsObject: JsObject =>
      fieldType.jsonToValue(jsObject \ fieldName)
  }

  private def displayStringGet[T](fieldType: FieldType[T], fieldName: String) = {
    jsObject: JsObject =>
      fieldType.jsonToDisplayStringOptional(jsObject \ fieldName)
  }

  private def jsonToDoublesAux[T](
    valueGets: Seq[JsObject => Option[T]]
  ) = { jsObject: JsObject => valueGets.map(_(jsObject))}

  private def jsonToValuesDefinedAux[T](
    valueGets: Seq[JsObject => Option[T]]
  ) = { jsObject: JsObject => valueGets.map(_(jsObject).getOrElse(
    throw new AdaConversionException(s"All values defined expected but got None for JSON:\n ${Json.prettyPrint(jsObject)}.")
  ))}
}
