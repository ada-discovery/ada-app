package util

import scala.reflect.runtime.universe._
import models.{AdaException, FieldTypeId, FieldTypeSpec}
import play.api.libs.json.JsValue
import java.{util => ju}

import reactivemongo.bson.BSONObjectID

import scala.collection.Traversable

object FieldUtil {

  def caseClassToFlatFieldTypes[T: TypeTag](delimiter: String = "."): Traversable[(String, FieldTypeSpec)] =
    caseClassToFlatFieldTypesAux(typeOf[T], delimiter)

  private def caseClassToFlatFieldTypesAux(typ: Type, delimiter: String): Traversable[(String, FieldTypeSpec)] = {
    val memberNamesAndTypes = dataaccess.ReflectionUtil.getCaseClassMemberNamesAndTypes(typ)

    memberNamesAndTypes.map { case (fieldName, memberType) =>
      try {
        val fieldTypeSpec = toFieldTypeSpec(memberType)
        Seq((fieldName, fieldTypeSpec))
      } catch {
        case e: AdaException => {
          val subFieldNameAndTypeSpecs = caseClassToFlatFieldTypesAux(memberType, delimiter)
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
  private def toFieldTypeSpec(typ: Type): FieldTypeSpec =
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
        val valueNames = getEnumValueNames(t).toSeq.sorted
        val enumMap = valueNames.zipWithIndex.map(_.swap).toMap
        FieldTypeSpec(FieldTypeId.Enum, false, Some(enumMap))

      // string
      case t if t matches typeOf[String] =>
        FieldTypeSpec(FieldTypeId.String)

      // date
      case t if t matches typeOf[ju.Date] =>
        FieldTypeSpec(FieldTypeId.Date)

      // string
      case t if t subMatches (typeOf[JsValue], typeOf[BSONObjectID]) =>
        FieldTypeSpec(FieldTypeId.Json)

      // array/seq
      case t if t subMatches (typeOf[Seq[_]], typeOf[Set[_]]) =>
        toFieldTypeSpec(t.typeArgs.head).copy(isArray = true)

      // otherwise
      case _ => throw new AdaException(s"Type ${typ.typeSymbol.fullName} unknown.")
    }
}
