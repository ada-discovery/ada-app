package dataaccess

import java.lang.reflect.InvocationTargetException

import dataaccess.ReflectionUtil._
import play.api.Logger
import java.{lang => jl}

import scala.collection.Traversable
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

object ReflectionUtil {

  private val mirror = ru.runtimeMirror(getClass.getClassLoader)

  def getMethodNames[T](implicit tag: ClassTag[T]): Traversable[String] =
    tag.runtimeClass.getMethods.map(_.getName)

  def getCaseClassMemberAndTypeNames[T: TypeTag]: Traversable[(String, String)] =
    getCaseClassMemberAndTypeNames(typeOf[T])

  def getCaseClassMemberAndTypeNames(className: String): Traversable[(String, String)] = {
    val runtimeType = classNameToRuntimeType(className)
    getCaseClassMemberAndTypeNames(runtimeType)
  }

  private def getCaseClassMemberAndTypeNames(runType: ru.Type): Traversable[(String, String)] =
    getCaseClassMemberNamesAndTypes(runType).map { case (name, ruType) =>
      (name, ruType.typeSymbol.asClass.fullName)
    }

  def isCaseClass(runType: ru.Type): Boolean =
    runType.members.exists( m => m.isMethod && m.asMethod.isCaseAccessor )

  def getCaseClassMemberNamesAndTypes(
    runType: ru.Type
  ): Traversable[(String, ru.Type)] =
    runType.decls.sorted.collect {
      case m: MethodSymbol if m.isCaseAccessor => (shortName(m), m.returnType)
    }

  def getCaseClassMemberNamesAndTypesInOrder(
    runType: ru.Type
  ): Traversable[(String, ru.Type)] =
    runType.decls.sorted.collect {
      case m: MethodSymbol if m.isCaseAccessor => (shortName(m), m.returnType)
    }

  def shortName(symbol: Symbol): String = {
    val paramFullName = symbol.fullName
    paramFullName.substring(paramFullName.lastIndexOf('.') + 1, paramFullName.length)
  }

  def classMirror(classSymbol: ClassSymbol): ClassMirror =
    mirror.reflectClass(classSymbol)

  def classNameToRuntimeType(name: String): ru.Type = {
    val sym = mirror.staticClass(name)
    sym.selfType
  }

  def typeToClass(typ: ru.Type): Class[_] =
    mirror.runtimeClass(typ.typeSymbol.asClass)

  def enumValueNames(typ: ru.Type): Traversable[String] =
    typ match {
      case TypeRef(enumType, _, _) => {
        val values = enumType.members.filter(sym => !sym.isMethod && sym.typeSignature.baseType(typ.typeSymbol) =:= typ)
        values.map(_.fullName.split('.').last)
      }
    }

  def enum(typ: ru.Type): Enumeration =
    typ match {
      case TypeRef(enumType, _, _) =>
        mirror.reflectModule(enumType.termSymbol.asModule).instance.asInstanceOf[Enumeration]
    }

  def javaEnumOrdinalValues[E <: Enum[E]](clazz: Class[E]): Map[Int, E] = {
    val enumValues = clazz.getEnumConstants()
    enumValues.map( value => (value.ordinal, value)).toMap
  }

  def construct[T](typ: Type, values: Seq[Any]): T =
    construct(typeToClass(typ).asInstanceOf[Class[T]], values)

  def construct[T](clazz: Class[T], values: Seq[Any]): T = {
    val boxedValues = values.map(box)

    def tryConstruct(index: Int): Option[T] = {
      try {
        val constructor = clazz.getConstructors()(index)
        val instance = constructor.newInstance(boxedValues: _*).asInstanceOf[T]
        Some(instance)
      } catch {
        case e: InstantiationException => None
        case e: IllegalAccessException => None
        case e: IllegalArgumentException => None
        case e: InvocationTargetException => None
      }
    }

    val num = clazz.getConstructors.length
    var instance: Option[T] = None
    var index = 0
    while (instance.isEmpty && index < num) {
      instance = tryConstruct(index)
      index += 1
    }

    instance.getOrElse(throwNoConstructorException(clazz, values))
  }

  private def throwNoConstructorException(clazz: Class[_], values: Seq[Any]) =
    throw new IllegalArgumentException(s"No suitable constructor could be found for the class ${clazz.getName} matching given params ${values.mkString(",")}.")

  def construct2[T](clazz: Class[T], values: Seq[Any]): T =
    try {
      val constructor = clazz.getDeclaredConstructor(values.map(_.getClass): _*)
      constructor.newInstance(values.map(box): _*)
    } catch {
      case e: NoSuchElementException => throwNoConstructorException(clazz, values)
      case e: SecurityException => throwNoConstructorException(clazz, values)
    }

  private def box(value: Any): AnyRef =
    value match {
      case x: AnyRef => x
      case x: Boolean => new jl.Boolean(x)
      case x: Double => new jl.Double(x)
      case x: Float => new jl.Float(x)
      case x: Short => new jl.Short(x)
      case x: Byte => new jl.Byte(x)
      case x: Int => new jl.Integer(x)
      case x: Long => new jl.Long(x)
      case _ => throw new IllegalArgumentException(s"Don't know how to box $value of type ${value.getClass.getName}.")
    }
}

trait DynamicConstructor[E] {
  def apply(fieldNameValues: Map[String, Any]): Option[E]
  def apply(valuesInOrder: Seq[Any]): Option[E]
}

trait DynamicConstructorFinder[E] {

  def apply(
    fieldNames: Seq[String],
    typeValueConverters: Traversable[(ru.Type, Any => Any)] = Nil
  ): Option[DynamicConstructor[E]]

  def classSymbol: ClassSymbol
}

object DynamicConstructorFinder {

  def apply[E: TypeTag]: DynamicConstructorFinder[E] =
    new DynamicConstructorFinderImpl[E](typeOf[E])

  def apply[E](className: String): DynamicConstructorFinder[E] =
    new DynamicConstructorFinderImpl[E](classNameToRuntimeType(className))

  def apply[E](typ: Type): DynamicConstructorFinder[E] =
    new DynamicConstructorFinderImpl[E](typ)
}

private class DynamicConstructorFinderImpl[E](runtimeType: ru.Type) extends DynamicConstructorFinder[E] {

  private val defaultTypeValues = Map[Type, Any](
    typeOf[Option[_]] -> None,
    typeOf[Boolean] -> false,
    typeOf[Seq[_]] -> Nil,
    typeOf[Set[_]] -> Set(),
    typeOf[Map[_, _]] -> Map()
  )

  override val classSymbol = runtimeType.typeSymbol.asClass
  private val cm = classMirror(classSymbol)

  private val constructorsWithInfos = runtimeType.decl(ru.termNames.CONSTRUCTOR).asTerm.alternatives.map{ ctor =>
    val constructor = cm.reflectConstructor(ctor.asMethod)

    val paramNameAndTypes = ctor.asMethod.paramLists.map(_.map{x => (shortName(x), x.info)}).flatten

    val paramNameDefaultValueMap: Map[String, Any] = paramNameAndTypes.map { case (paramName, paramType) =>
      val defaultValueOption = defaultTypeValues.find {
        case (defaultType, defaultValue) => paramType <:< defaultType
      }.map(_._2)
      defaultValueOption.map( defaultValue => (paramName, defaultValue))
    }.flatten.toMap

    (constructor, paramNameAndTypes, paramNameDefaultValueMap)
  }.sortBy(-_._2.size)

  // chooses the first constructor (the one that satisfies the most parameters...see sorting in the declaration);
  // alternatively could throw an exception or log a warning saying that multiple constructors could be applied
  override def apply(
    fieldNames: Seq[String],
    typeValueConverters: Traversable[(ru.Type, Any => Any)]
  ): Option[DynamicConstructor[E]] = {
    val constructorWithInfosOption = constructorsWithInfos.find { case (constructor, paramNameAndTypes, paramNameDefaultValueMap) =>
      paramNameAndTypes.forall { case (paramName, _) =>
        fieldNames.contains(paramName) || paramNameDefaultValueMap.contains(paramName)
      }
    }

    constructorWithInfosOption.map { case (constructor, paramNameAndTypes, paramNameDefaultValueMap) =>
        new DynamicConstructorImpl[E](constructor, paramNameAndTypes, paramNameDefaultValueMap, classSymbol, fieldNames, typeValueConverters)
    }
  }
}

private class DynamicConstructorImpl[E](
    constructor: MethodMirror,
    paramNameAndTypes: List[(String, ru.Type)],
    paramNameDefaultValueMap: Map[String, Any],
    reflectedClass: ClassSymbol,
    fieldNames: Seq[String],
    typeValueConverters: Traversable[(ru.Type, Any => Any)]
  ) extends DynamicConstructor[E] {

  private val logger = Logger

  private lazy val fieldConstructorIndeces = {
    val paramNameIndexMap = paramNameAndTypes.map(_._1).zipWithIndex.toMap
    fieldNames.map(paramNameIndexMap.get(_).get)
  }

  private lazy val constructorValues = paramNameAndTypes.map{ case (paramName, _) =>
    if (!fieldNames.contains(paramName)) {
      // failover to default values (we know it exists due to the search performed above)
      paramNameDefaultValueMap.get(paramName).get
    } else
      None
    }.toSeq

  def apply(fieldNameValueMap: Map[String, Any]): Option[E] =
    try {
      val constructorValues = paramNameAndTypes.map { case (paramName, paramType) =>
        fieldNameValueMap.get(paramName).map { value =>
          // convert the value if needed
          typeValueConverters.find(_._1.=:=(paramType)).map { case (_, converter) =>
            converter(value)
          }.getOrElse(
            value
          )
        }.getOrElse {
          // failing over to default values
          paramNameDefaultValueMap.get(paramName).getOrElse(
            throw new IllegalArgumentException(s"Constructor of ${reflectedClass.fullName} expects mandatory param '${paramName}' but the result set contains none.")
          )
        }
      }
      Some(
        constructor(constructorValues: _*).asInstanceOf[E]
      )
    } catch {
      case e: Exception => {
        logger.error(s"Dynamic constructor of ${reflectedClass.fullName} invocation failed.", e)
        None
      }
    }

  override def apply(valuesInOrder: Seq[Any]): Option[E] =
    if (fieldNames.size != valuesInOrder.size)
      None
    else {
      val newValues: scala.collection.mutable.Seq[Any] = scala.collection.mutable.ArraySeq(constructorValues:_*)
      (fieldConstructorIndeces, valuesInOrder).zipped.map{ (index, value) =>
        newValues.update(index, value)
      }
      Some(
        constructor(newValues: _*).asInstanceOf[E]
      )
    }
}