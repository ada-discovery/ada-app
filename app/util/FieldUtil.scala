package util

import scala.reflect.runtime.universe._
import models.{AdaException, Field, FieldTypeId, FieldTypeSpec}
import play.api.libs.json.JsValue
import java.{util => ju}
import dataaccess.Criterion._

import dataaccess.RepoTypes.FieldRepo
import dataaccess.{FieldTypeHelper, ReflectionUtil => CoreReflectionUtil}
import models.DataSetFormattersAndIds.FieldIdentity
import reactivemongo.bson.BSONObjectID

import scala.collection.Traversable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FieldUtil {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  def valueConverters(
    fields: Traversable[Field]
  ): Map[String, String => Option[Any]] =
    fields.map { field =>
      val fieldType = ftf(field.fieldTypeSpec)
      val converter = { text: String => fieldType.valueStringToValue(text) }
      (field.name, converter)
    }.toMap

  def valueConverters(
    fieldRepo: FieldRepo,
    fieldNames: Traversable[String]
  ): Future[Map[String, String => Option[Any]]] =
    for {
      fields <- if (fieldNames.nonEmpty)
        fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSeq))
      else
        Future(Nil)
    } yield
      valueConverters(fields)

  def caseClassToFlatFieldTypes[T: TypeTag](
    delimiter: String = ".",
    excludedFieldSet: Set[String] = Set(),
    treatEnumAsString: Boolean = false
  ): Traversable[(String, FieldTypeSpec)] =
    caseClassTypeToFlatFieldTypes(typeOf[T], delimiter, excludedFieldSet, treatEnumAsString)

  def caseClassTypeToFlatFieldTypes(
    typ: Type,
    delimiter: String = ".",
    excludedFieldSet: Set[String] = Set(),
    treatEnumAsString: Boolean = false
  ): Traversable[(String, FieldTypeSpec)] = {
    val memberNamesAndTypes = CoreReflectionUtil.getCaseClassMemberNamesAndTypes(typ).filter(x => !excludedFieldSet.contains(x._1))

    memberNamesAndTypes.map { case (fieldName, memberType) =>
      try {
        val fieldTypeSpec = toFieldTypeSpec(memberType, treatEnumAsString)
        Seq((fieldName, fieldTypeSpec))
      } catch {
        case e: AdaException => {
          val subType = unwrapIfOption(memberType)

          val subFieldNameAndTypeSpecs = caseClassTypeToFlatFieldTypes(subType, delimiter, excludedFieldSet, treatEnumAsString)
          if (subFieldNameAndTypeSpecs.isEmpty)
            throw e
          else
            subFieldNameAndTypeSpecs.map { case (subFieldName, x) => (s"$fieldName$delimiter$subFieldName", x)}
        }
      }
    }.flatten
  }

  implicit class InfixOp(val typ: Type) {

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
    val enumValueType = unwrapIfOption(typ)

    enumValueType match {
      case TypeRef(enumType, _, _) => {
        val values = enumType.members.filter(sym => !sym.isMethod && sym.typeSignature.baseType(enumValueType.typeSymbol) =:= enumValueType)
        values.map(_.fullName.split('.').last)
      }
    }
  }

  private def getJavaEnumOrdinalValues[E <: Enum[E]](typ: Type): Map[Int, String] = {
    val enumType = unwrapIfOption(typ)
    val clazz = CoreReflectionUtil.typeToClass(enumType).asInstanceOf[Class[E]]
    val enumValues = CoreReflectionUtil.javaEnumOrdinalValues(clazz)
    enumValues.map { case (ordinal, value) => (ordinal, value.toString) }
  }

  private def unwrapIfOption(typ: Type) =
    if (typ <:< typeOf[Option[_]]) typ.typeArgs.head else typ

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

      // Java enum
      case t if t subMatches typeOf[Enum[_]] =>
        if (treatEnumAsString)
          FieldTypeSpec(FieldTypeId.String)
        else {
          val enumMap = getJavaEnumOrdinalValues(t)
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