package util

import java.io.File

import org.clapper.classutil.{ClassFinder, ClassInfo}
import play.api.Logger

import scala.reflect.ClassTag

object ClassFinderUtil {

//  private val tomcatLibFolder = "../webapps/ROOT/WEB-INF/lib/"
//  private val userDir = System.getProperty("user.dir")
  private val defaultRootLibFolder = "lib"
  private val logger = Logger

  def findClasses[T](
    libPrefix: String,
    packageName: Option[String],
    packageFullMatch: Boolean,
    className: Option[String],
    libFolder: Option[String]
  )(implicit m: ClassTag[T]): Stream[Class[T]] = {
    val filteredClassInfos = findClassInfos[T](libPrefix, packageName, packageFullMatch, className, libFolder)
    filteredClassInfos.map{ classInfo =>
      Class.forName(classInfo.name).asInstanceOf[Class[T]]
    }
  }

  private def streamClassInfos(libPrefix: String, libFolder: Option[String]): Stream[ClassInfo] = {
    val defaultClasspath = new File(".")
    logger.info("Searching libs in a default classpath: " + defaultClasspath.getAbsolutePath)

    val libClasspath = new File(libFolder.getOrElse(defaultRootLibFolder))
    logger.info("Searching libs in a custom classpath: " + libClasspath.getAbsolutePath)

//    logger.info("User dir: " + userDir)

    val libFiles = libClasspath.getAbsoluteFile.listFiles

    val classpath : List[File] =
      if (libFiles != null) {
        logger.info(s"Found ${libFiles.length} files in a custom classpath.")

        val extClasspath = libFiles.filter(file =>
          file.isFile && file.getName.startsWith(libPrefix) && file.getName.endsWith(".jar")
        )

        logger.info(s"Found ${extClasspath.length} libs matching a prefix ${libPrefix}.")

        (extClasspath.toSeq ++ Seq(defaultClasspath)).toList
      } else
        List(defaultClasspath)

    ClassFinder(classpath).getClasses
  }

  private def findClassInfos[T](
    libPrefix: String,
    packageName: Option[String],
    packageFullMatch: Boolean,
    className: Option[String],
    libFolder: Option[String]
  )(implicit m: ClassTag[T]): Stream[ClassInfo] = {
    val clazz = m.runtimeClass

    val classInfos = streamClassInfos(libPrefix, libFolder)
    classInfos.filter{ classInfo =>
      val foundClassName = classInfo.name

      // package match
      val packageMatched = packageName.map { packageName =>
        val lastDot = foundClassName.lastIndexOf('.')
        if (lastDot > -1) {
          val foundPackageName = foundClassName.substring(0, lastDot)
          if (packageFullMatch)
            foundPackageName.equals(packageName)
          else
            foundPackageName.startsWith(packageName)
        } else
          false
      }.getOrElse(true)

      try {
        packageMatched &&
        className.map(foundClassName.endsWith(_)).getOrElse(true) &&
        classInfo.isConcrete &&
        !classInfo.isSynthetic &&
        !classInfo.name.contains("$") &&
        clazz.isAssignableFrom(Class.forName(foundClassName))
      } catch {
        case _ : ClassNotFoundException => false
        case _ : ExceptionInInitializerError => false
        case _ : NoClassDefFoundError => false
      }
    }
  }
}