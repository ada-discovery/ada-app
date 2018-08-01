package util

import java.io.File

import org.clapper.classutil.{ClassFinder, ClassInfo}
import scala.reflect.ClassTag

object ReflectionUtil {

//  private val tomcatLibFolder = "../webapps/ROOT/WEB-INF/lib/"
  private val nettyLibFolder = "../lib/"

  def streamClassInfos(libPrefix: String): Stream[ClassInfo] = {
    val defaultClasspath = List(".").map(new File(_))

    val tomcatLibFiles = new File(nettyLibFolder).listFiles
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
    val filteredClassInfos = findClassInfos[T](libPrefix, packageName, className)
    filteredClassInfos.map{ classInfo =>
      Class.forName(classInfo.name).asInstanceOf[Class[T]]
    }
  }

  private def findClassInfos[T](
    libPrefix: String,
    packageName: Option[String],
    className: Option[String]
  )(implicit m: ClassTag[T]): Stream[ClassInfo] = {
    val clazz = m.runtimeClass
    val classInfos = streamClassInfos(libPrefix)
    classInfos.filter{ classInfo =>
      try {
        packageName.map(classInfo.name.startsWith(_)).getOrElse(true) &&
          className.map(classInfo.name.endsWith(_)).getOrElse(true) &&
          classInfo.isConcrete &&
          !classInfo.isSynthetic &&
          !classInfo.name.contains("$") &&
          clazz.isAssignableFrom(Class.forName(classInfo.name))
      } catch {
        case _ : ClassNotFoundException => false
        case _ : ExceptionInInitializerError => false
        case _ : NoClassDefFoundError => false
      }
    }
  }

  def getMethodNames[T](implicit tag: ClassTag[T]): Array[String] =
    tag.runtimeClass.getMethods.map(_.getName)
}