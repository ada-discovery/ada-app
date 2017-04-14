package util

import java.io.File

import org.clapper.classutil.{ClassFinder, ClassInfo}

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object ReflectionUtil {

  private val tomcatLibFolder = "../webapps/ROOT/WEB-INF/lib/"

  def streamClassInfos(libPrefix: String): Stream[ClassInfo] = {
    val defaultClasspath = List(".").map(new File(_))

    val tomcatLibFiles = new File(tomcatLibFolder).listFiles
    val classpath : List[File] =
      if (tomcatLibFiles != null) {
        val tomcatClasspath = tomcatLibFiles.filter(file =>
          file.isFile && file.getName.startsWith(libPrefix) && file.getName.endsWith(".jar"))
        (tomcatClasspath ++ defaultClasspath).toList
      } else
        defaultClasspath

    ClassFinder(classpath).getClasses
  }

  def findClasses[T](
    libPrefix: String,
    packageName: Option[String],
    className: Option[String]
  )(implicit m: ClassTag[T]): Stream[Class[T]] = {
    val clazz = m.runtimeClass
    val classInfos = streamClassInfos(libPrefix)
    val filteredClassInfos = classInfos.filter{ classInfo =>
      try {
        packageName.map(classInfo.name.startsWith(_)).getOrElse(true) &&
          className.map(classInfo.name.endsWith(_)).getOrElse(true) &&
          classInfo.isConcrete &&
          clazz.isAssignableFrom(Class.forName(classInfo.name))
      } catch {
        case _ : ClassNotFoundException => false
        case _ : ExceptionInInitializerError => false
        case _ : NoClassDefFoundError => false
      }
    }
    filteredClassInfos.map{ classInfo => Class.forName(classInfo.name).asInstanceOf[Class[T]] }
  }

  def getMethodNames[T](implicit tag: ClassTag[T]): Array[String] =
    tag.runtimeClass.getMethods.map(_.getName)
}
