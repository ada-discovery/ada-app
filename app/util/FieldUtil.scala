package util

import scala.reflect.runtime.universe._
import models.{AdaException, FieldTypeId, FieldTypeSpec}
import play.api.libs.json.JsValue
import java.{util => ju}

import reactivemongo.bson.BSONObjectID

import scala.collection.Traversable

object FieldUtil {

  def caseClassToFlatFieldTypes[T: TypeTag](
    delimiter: String = ".",
    excludedFieldSet: Set[String] = Set(),
    treatEnumAsString: Boolean = false
  ): Traversable[(String, FieldTypeSpec)] =
    caseClassToFlatFieldTypesAux(typeOf[T], delimiter, excludedFieldSet, treatEnumAsString)

  private def caseClassToFlatFieldTypesAux(
    typ: Type,
    delimiter: String,
    excludedFieldSet: Set[String],
    treatEnumAsString: Boolean
  ): Traversable[(String, FieldTypeSpec)] = {
    val memberNamesAndTypes = dataaccess.ReflectionUtil.getCaseClassMemberNamesAndTypes(typ).filter(x => !excludedFieldSet.contains(x._1))

    memberNamesAndTypes.map { case (fieldName, memberType) =>
      try {
        val fieldTypeSpec = toFieldTypeSpec(memberType, treatEnumAsString)
        Seq((fieldName, fieldTypeSpec))
      } catch {
        case e: AdaException => {
          val subFieldNameAndTypeSpecs = caseClassToFlatFieldTypesAux(memberType, delimiter, excludedFieldSet, treatEnumAsString)
          if (subFieldNameAndTypeSpecs.isEmpty)
            throw e
          else
            subFieldNameAndTypeSpecs.map { case (subFieldName, x) => (s"$fieldName$delimiter$subFieldName", x)}
        }
      }
    }.flatten
  }

  implicit class Infix(val typ: Type) {

    private val optionInnerType =
      if (typ <:< typeOf[Option[_]])
        Some(typ.typeArgs.head)
      else
        None

    def matches(types: Type*) =
      types.exists(typ =:= _) ||
        (optionInnerType.isDefined && types.exists(optionInnerType.get =:= _))

    def subMatches(types: Type*) =
      types.exists(typ <:< _) ||
        (optionInnerType.isDefined && types.exists(optionInnerType.get <:< _))
  }

  private def getEnumValueNames(typ: Type): Traversable[String] = {
    val enumValueType =
      if (typ <:< typeOf[Option[_]])
        typ.typeArgs.head
      else
        typ

    enumValueType match {
      case TypeRef(enumType, _, _) => {
        val values = enumType.members.filter(sym => !sym.isMethod && sym.typeSignature.baseType(enumValueType.typeSymbol) =:= enumValueType)
        values.map(_.fullName.split('.').last)
      }
    }
  }

  @throws(classOf[AdaException])
  private def toFieldTypeSpec(
    typ: Type,
    treatEnumAsString: Boolean
  ): FieldTypeSpec =
    typ match {
      // double
      case t if t matches (typeOf[Double], typeOf[Float], typeOf[BigDecimal]) =>
        FieldTypeSpec(FieldTypeId.Double)

      // int
      case t if t matches (typeOf[Int], typeOf[Long], typeOf[Byte]) =>
        FieldTypeSpec(FieldTypeId.Integer)

      // boolean
      case t if t matches typeOf[Boolean] =>
        FieldTypeSpec(FieldTypeId.Boolean)

      // enum
      case t if t subMatches typeOf[Enumeration#Value] =>
        if (treatEnumAsString)
          FieldTypeSpec(FieldTypeId.String)
        else {
          val valueNames = getEnumValueNames(t).toSeq.sorted
          val enumMap = valueNames.zipWithIndex.map(_.swap).toMap
          FieldTypeSpec(FieldTypeId.Enum, false, Some(enumMap))
        }

      // string
      case t if t matches typeOf[String] =>
        FieldTypeSpec(FieldTypeId.String)

      // date
      case t if t matches typeOf[ju.Date] =>
        FieldTypeSpec(FieldTypeId.Date)

      // json
      case t if t subMatches (typeOf[JsValue], typeOf[BSONObjectID]) =>
        FieldTypeSpec(FieldTypeId.Json)

      // array/seq
      case t if t subMatches (typeOf[Seq[_]], typeOf[Set[_]]) =>
        val innerType = t.typeArgs.head
        try {
          toFieldTypeSpec(innerType, treatEnumAsString).copy(isArray = true)
        } catch {
          case e: AdaException => FieldTypeSpec(FieldTypeId.Json, true)
        }

      // otherwise
      case _ =>
        val typeName =
          if (typ <:< typeOf[Option[_]])
            s"Option[${typ.typeArgs.head.typeSymbol.fullName}]"
          else
            typ.typeSymbol.fullName
        throw new AdaException(s"Type ${typeName} unknown.")
    }
}
