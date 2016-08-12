package dataaccess

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object ReflectionUtil {

  def getMethodNames[T](implicit tag: ClassTag[T]): Traversable[String] =
    tag.runtimeClass.getMethods.map(_.getName)

  def getCaseMethodNamesAndTypes[T: TypeTag]: Traversable[(String, String)] =
    typeOf[T].members.collect {
      case m: MethodSymbol if m.isCaseAccessor => {
        (shortName(m), m.returnType.typeSymbol.asClass.fullName)
      }
    }.toList

  def shortName(symbol: Symbol): String = {
    val paramFullName = symbol.fullName
    paramFullName.substring(paramFullName.lastIndexOf('.') + 1, paramFullName.length)
  }
}
