package controllers.core

import controllers.{BSONObjectIDStringFormatter, EnumFormatter, JsonFormatter}
import models.{AdaException, StorageType}
import play.api.data._
import play.api.data.Forms.{mapping, _}
import play.api.data.format.Formats._
import play.api.data.validation.Constraint

import scala.reflect.runtime.universe._
import play.api.data._
import java.{util => ju}

import dataaccess.ReflectionUtil
import reactivemongo.bson.BSONObjectID

import scala.collection.Traversable

private class GenericMapping[R, A](
    apply: Function1[Traversable[A], R],
    unapply: Function1[R, Option[Traversable[A]]],
    fs: Traversable[(String, Mapping[A])],
    val key: String = "",
    val constraints: Seq[Constraint[R]] = Nil
  ) extends Mapping[R] with ObjectMapping {

  val fields = fs.map(f => f._2.withPrefix(f._1).withPrefix(key))

  def bind(data: Map[String, String]): Either[Seq[FormError], R] = {
    merge(fields.map(_.bind(data)).toSeq :_*) match {
      case Left(errors) => Left(errors)
      case Right(values) => {
        applyConstraints(apply(values.map(_.asInstanceOf[A])))
      }
    }
  }

  def unbind(value: R): Map[String, String] = {
    unapply(value).map { values =>
      val maps = (fields.toSeq, values.toSeq).zipped.map { case (field, value) =>
        field.unbind(value)
      }
      maps.fold(Map[String, String]()){ _ ++ _}
    }.getOrElse(Map.empty)
  }

  def unbindAndValidate(value: R): (Map[String, String], Seq[FormError]) = {
    unapply(value).map { values =>
      val maps = (fields.toSeq, values.toSeq).zipped.map { case (field, value) =>
        field.unbindAndValidate(value)
      }
      maps.fold((Map[String, String](), Seq[FormError]())){ case (a, b) =>
        (a._1 ++ b._1, a._2 ++ b._2)
      }
    }.getOrElse(Map.empty[String, String] -> Seq(FormError(key, "unbind.failed")))
  }

  def withPrefix(prefix: String): GenericMapping[R, A] = addPrefix(prefix).map(newKey =>
    new GenericMapping(apply, unapply, fs, newKey, constraints)
  ).getOrElse(this)

  def verifying(addConstraints: Constraint[R]*): GenericMapping[R, A] = {
    new GenericMapping(apply, unapply, fs, key, constraints ++ addConstraints.toSeq)
  }

  val mappings = Seq(this) ++ fields.flatMap(_.mappings)
}

object GenericMapping {

  def apply[A](fs: Traversable[(String, Mapping[A])]): Mapping[Traversable[A]] =
    new GenericMapping[Traversable[A], A](identity[Traversable[A]], Some(_), fs)

  def apply[T](typ: Type): Mapping[T] =
    if (ReflectionUtil.isCaseClass(typ))
      applyCaseClass(typ)
    else
      applySimpleType(typ)

  def applySimpleType[T](typ: Type): Mapping[T] =
    genericMapping(typ).asInstanceOf[Mapping[T]]

  def applyCaseClass[T](typ: Type): Mapping[T] = {
    val mappings = caseClassMapping(typ)
    // ugly but somehow class information could be lost it the process (if a runtime type is used)
    val clazz = Class.forName(typ.typeSymbol.fullName).asInstanceOf[Class[T]]

    new GenericMapping[T, Any](
      values => ReflectionUtil.construct[T](clazz, values.toSeq),
      item => Some(item.asInstanceOf[Product].productIterator.toSeq),
      mappings
    )
  }

  private def caseClassMapping(
    typ: Type
  ): Traversable[(String, Mapping[Any])] = {
    val memberNamesAndTypes = ReflectionUtil.getCaseClassMemberNamesAndTypesInOrder(typ)

    memberNamesAndTypes.map { case (fieldName, memberType) =>
      val mapping =
        try {
          genericMapping(memberType)
        } catch {
          case e: AdaException => failover(memberType, e)
        }
      (fieldName, mapping)
    }
  }

  // helper method to recover if a given member type cannot be recognized
  @throws(classOf[AdaException])
  private def failover(memberType: Type, e: AdaException): Mapping[Any] =
    // check if it's the member type is a case class and apply recurrently
    if (ReflectionUtil.isCaseClass(memberType)) {
      GenericMapping.applyCaseClass[Any](memberType)
    } else if (memberType <:< typeOf[Option[_]]) {
      val typ = memberType.typeArgs.head

      // if it's an option type continue with its inner type (for a case class)
      if (ReflectionUtil.isCaseClass(typ)) {
        val mapping = GenericMapping.applyCaseClass[Any](typ)
        optional(mapping).asInstanceOf[Mapping[Any]]
      } else {
        // otherwise ignore
        ignored(None)
      }
    } else
      throw e

  private implicit class Infix(val typ: Type) {
    def matches(types: Type*) = types.exists(typ =:= _)

    def subMatches(types: Type*) = types.exists(typ <:< _)
  }

  private implicit val bsonObjectIDFormatter = BSONObjectIDStringFormatter

  @throws(classOf[AdaException])
  private def genericMapping(typ: Type): Mapping[Any] = {
    val mapping = typ match {
      // float
      case t if t matches typeOf[Float] =>
        of[Float]

      // double
      case t if t matches typeOf[Double] =>
        of[Double]

      // bigdecimal
      case t if t matches typeOf[BigDecimal] =>
        bigDecimal

      // short
      case t if t matches typeOf[Short] =>
        shortNumber

      // byte
      case t if t matches typeOf[Byte] =>
        byteNumber

      // int
      case t if t matches typeOf[Int] =>
        number

      // long
      case t if t matches typeOf[Long] =>
        longNumber

      // boolean
      case t if t matches typeOf[Boolean] =>
        boolean

      // enum
      case t if t subMatches typeOf[Enumeration#Value] =>
 //       val enumNames = ReflectionUtil.enumValueNames(t)
        val enum = ReflectionUtil.enum(t)
        of(EnumFormatter(enum))
//        t match {
//          case TypeRef(enumType, _, _) => {
//            val withNameMethod = enumType.member(newTermName("withName"))
//            val lalla = withNameMethod.asMethod
//          }
//        }
//        boolean
//        of(EnumFormatter(t)) // TODO

      // string
      case t if t matches typeOf[String] =>
        nonEmptyText

      // date
      case t if t matches typeOf[ju.Date] =>
        date

      // BSON Object Id
      case t if t matches typeOf[BSONObjectID] =>
        of[BSONObjectID]

      // optional
      case t if t <:< typeOf[Option[_]] =>
        optional(genericMapping(t.typeArgs.head))

      // seq
      case t if t subMatches typeOf[Seq[_]] =>
        val innerType = t.typeArgs.head
        seq(genericMapping(innerType))

      // set
      case t if t subMatches typeOf[Set[_]] =>
        val innerType = t.typeArgs.head
        set(genericMapping(innerType))

      // list
      case t if t subMatches typeOf[List[_]] =>
        val innerType = t.typeArgs.head
        list(genericMapping(innerType))

      // otherwise
      case _ =>
        val typeName =
          if (typ <:< typeOf[Option[_]])
            s"Option[${typ.typeArgs.head.typeSymbol.fullName}]"
          else
            typ.typeSymbol.fullName
        throw new AdaException(s"Mapping for type ${typeName} unknown.")
    }

    mapping.asInstanceOf[Mapping[Any]]
  }
}

case class TestCaseClass(name: String, label: Option[String], age: Int, storageType: Option[StorageType.Value])

object ObjectListMappingTest extends App {
  val mapping = GenericMapping.applyCaseClass[TestCaseClass](typeOf[TestCaseClass])
  mapping.mappings.foreach(mapping =>
    println(mapping)
  )

  val simpleMapping = GenericMapping.apply[String](typeOf[String])
  println(simpleMapping)
}