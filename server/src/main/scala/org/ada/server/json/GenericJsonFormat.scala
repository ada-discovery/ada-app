package org.ada.server.json

import org.ada.server.AdaException
import org.incal.core.util.ReflectionUtil
import org.incal.core.util.ReflectionUtil.getCaseClassMemberNamesAndTypes
import play.api.libs.json.{Format, OFormat}
import reactivemongo.bson.BSONObjectID
import play.api.libs.json._
import java.{util => ju}

import com.bnd.network.domain.ActivationFunctionType
import org.ada.server.models.{RunnableSpec, ScheduledTime, User, WeekDay}
import play.api.libs.functional.syntax._
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

import scala.reflect.runtime.universe._
import org.incal.core.util.ReflectionUtil._

object GenericJsonFormat {

  def apply[E: TypeTag]: Format[E] = {
    val typ = typeOf[E]
    genericFormat(typ, newCurrentThreadMirror).asInstanceOf[Format[E]]
  }

  private implicit class Infix(val typ: Type) {
    def matches(types: Type*) = types.exists(typ =:= _)

    def subMatches(types: Type*) = types.exists(typ <:< _)
  }

  @throws(classOf[AdaException])
  private def genericFormat(
    typ: Type,
    mirror: Mirror
  ): Format[Any] = {
    val format = typ match {
      // float
      case t if t matches typeOf[Float] =>
        implicitly[Format[Float]]

      // double
      case t if t matches typeOf[Double] =>
        implicitly[Format[Double]]

      // bigdecimal
      case t if t matches typeOf[BigDecimal] =>
        implicitly[Format[BigDecimal]]

      // short
      case t if t matches typeOf[Short] =>
        implicitly[Format[Short]]

      // byte
      case t if t matches typeOf[Byte] =>
        implicitly[Format[Byte]]

      // int
      case t if t matches typeOf[Int] =>
        implicitly[Format[Int]]

      // long
      case t if t matches typeOf[Long] =>
        implicitly[Format[Long]]

      // boolean
      case t if t matches typeOf[Boolean] =>
        implicitly[Format[Boolean]]

      // string
      case t if t matches typeOf[String] =>
        implicitly[Format[String]]

      // date
      case t if t matches typeOf[ju.Date] =>
        implicitly[Format[ju.Date]]

      // BSON Object Id
      case t if t matches typeOf[BSONObjectID] =>
        BSONObjectIDFormat

      // enum
      case t if t subMatches typeOf[Enumeration#Value] =>
        val enum = ReflectionUtil.enum(t, mirror)
        EnumFormat(enum)

      // Java enum
      case t if t subMatches typeOf[Enum[_]] =>
        val clazz = typeToClass(t, mirror)
        JavaEnumFormat.applyClassUnsafe(clazz)

      // optional
      case t if t <:< typeOf[Option[_]] =>
        implicit val innerFormat = genericFormat(t.typeArgs.head, mirror)
        Format.optionWithNull(innerFormat)
//        new OptionFormat[Any]

      // seq
      case t if t subMatches typeOf[Seq[_]] =>
        val typeArgs = t.typeArgs
        val innerFormat = genericFormat(typeArgs(0), mirror)
        val writesSeq = Writes.seq[Any](innerFormat)
        val readsSeq = Reads.seq[Any](innerFormat)
        Format.GenericFormat(readsSeq, writesSeq)

      // set
      case t if t subMatches typeOf[Set[_]] =>
        val typeArgs = t.typeArgs
        val innerFormat = genericFormat(typeArgs(0), mirror)
        val writesSeq = Writes.set[Any](innerFormat)
        val readsSeq = Reads.set[Any](innerFormat)
        Format.GenericFormat(readsSeq, writesSeq)

      // list
      case t if t subMatches typeOf[List[_]] =>
        val typeArgs = t.typeArgs
        val innerFormat = genericFormat(typeArgs(0), mirror)
        val writesSeq = Writes.list[Any](innerFormat)
        val readsSeq = Reads.list[Any](innerFormat)
        Format.GenericFormat(readsSeq, writesSeq)

      // traversable
      case t if t subMatches typeOf[Traversable[_]] =>
        val typeArgs = t.typeArgs
        val innerFormat = genericFormat(typeArgs(0), mirror)
        val writesSeq = Writes.traversableWrites[Any](innerFormat)
        val readsSeq = Reads.seq[Any](innerFormat)
        Format.GenericFormat(readsSeq, writesSeq)

      // tuple2
      case t if t subMatches typeOf[Tuple2[_, _]] =>
        val typeArgs = t.typeArgs
        val formatA = genericFormat(typeArgs(0), mirror)
        val formatB = genericFormat(typeArgs(1), mirror)
        TupleFormat(formatA, formatB)

      // tuple3
      case t if t subMatches typeOf[Tuple3[_, _, _]] =>
        val typeArgs = t.typeArgs
        val formatA = genericFormat(typeArgs(0), mirror)
        val formatB = genericFormat(typeArgs(1), mirror)
        val formatC = genericFormat(typeArgs(2), mirror)
        TupleFormat(formatA, formatB, formatC)

      // either
      case t if t subMatches typeOf[Either[_, _]] =>
        val typeArgs = t.typeArgs
        val formatA = genericFormat(typeArgs(0), mirror)
        val formatB = genericFormat(typeArgs(1), mirror)
        EitherFormat(formatA, formatB)

      // case class
      case x if isCaseClass(x) =>
        caseClassFormat(typ, mirror)

      // otherwise
      case _ =>
        val typeName =
          if (typ <:< typeOf[Option[_]])
            s"Option[${typ.typeArgs.head.typeSymbol.fullName}]"
          else
            typ.typeSymbol.fullName
        throw new AdaException(s"Format for type ${typeName} unknown.")
    }

    format.asInstanceOf[Format[Any]]
  }

  private def caseClassFormat(
    typ: Type,
    mirror: Mirror
  ): Format[Any] = {
    val memberNamesAndTypes = getCaseClassMemberNamesAndTypes(typ)

    val emptySeqFormat = OFormat.apply[Seq[Any]]((jsValue: JsValue) => JsSuccess(Nil), (seq: Seq[Any]) => Json.obj())

    val finalMergedListFormat = memberNamesAndTypes.foldLeft(emptySeqFormat) {
      case (cumFormat: OFormat[Seq[Any]], (fieldName: String, memberType: Type)) =>
        val format = genericFormat(memberType, mirror)
        val currentFormat = (__ \ fieldName).format[Any](format)
        val mergedListFormat: OFormat[Seq[Any]] = (cumFormat and currentFormat).apply[Seq[Any]](
          (seq: Seq[Any], element: Any) => seq ++ Seq(element),
          (seq: Seq[Any]) => (seq.dropRight(1), seq.last)
        )
        mergedListFormat
    }

    val finalFormat = new CaseClassSeqFormat[Product](typ, finalMergedListFormat)
    finalFormat.asInstanceOf[Format[Any]]
  }

  private class CaseClassSeqFormat[E <: Product](typ: Type, listFormat: Format[Seq[Any]]) extends Format[E] {

    override def writes(o: E): JsValue =
      listFormat.writes(o.productIterator.toSeq)

    override def reads(json: JsValue): JsResult[E] =
      listFormat.reads(json) match {
        case JsSuccess(v, path) =>
          val item = ReflectionUtil.construct[E](typ, v)
          JsSuccess(item, path)

        case JsError(e) =>
          JsError(e)
      }
  }
}

object GenericJsonFormatTest extends App {

  implicit val userFormat = GenericJsonFormat.apply[User]

  val user = User(None, "peter", "peter@peter.net", Seq("admin", "runner"))
  val userJson = Json.toJson(user)
  val user2 = userJson.as[User]

  println(Json.prettyPrint(userJson))
  println(user2)

  val runnableSpec = RunnableSpec(
    _id = Some(BSONObjectID.generate),
    runnableClassName = "org.ada.Lala",
    name = "Lala",
    scheduled = true,
    scheduledTime = Some(ScheduledTime(
      weekDay = Some(WeekDay.Friday),
      hour = Some(12),
      minute = None,
      second = None
    ))
  )

  implicit val runnableFormat = GenericJsonFormat.apply[RunnableSpec]
  val runnableJson = Json.toJson(runnableSpec)
  val runnableSpec2 = runnableJson.as[RunnableSpec]

  println(Json.prettyPrint(runnableJson))
  println(runnableSpec2)
}