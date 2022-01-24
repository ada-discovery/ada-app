package runnables.mpower

import org.incal.core.runnables.{InputRunnable, InputRunnableExt}
import org.incal.core.util.listFiles

import scala.io.Source
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import org.ada.server.util.ManageResource.using

import scala.reflect.runtime.universe.typeOf

class MergeHeaders extends InputRunnableExt[MergeHeadersSpec] {

  override def run(input: MergeHeadersSpec) = {
    val newHeaders = listFiles(input.folderPath).sortBy(_.getName).zipWithIndex.map {
      case (headerFile, index) =>
        using(Source.fromFile(headerFile)){
          source => {
            val headerName = headerFile.getName.split('.').head
            val header = source.getLines().next().replaceAll("\"", "")

            val headerItems = header.split(",")

            val newHeaderItems = headerItems.map { columnName =>
              headerName + "-" + columnName
            }

            if (index == 0)
              headerItems.head + "," + newHeaderItems.tail.mkString(",")
            else
              newHeaderItems.tail.mkString(",")
          }
        }
    }

    println
    println("New Headers:")
    println("------------")

    newHeaders.foreach(println)

    Files.write(
      Paths.get(input.folderPath + "/merged"),
      newHeaders.mkString(",").getBytes(StandardCharsets.UTF_8)
    )
  }
}

case class MergeHeadersSpec(folderPath: String)