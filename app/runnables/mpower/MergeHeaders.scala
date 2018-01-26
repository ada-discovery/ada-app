package runnables.mpower

import runnables.InputRunnable
import util.getListOfFiles

import scala.io.Source
import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets
import scala.reflect.runtime.universe.typeOf

class MergeHeaders extends InputRunnable[MergeHeadersSpec] {

  override def run(input: MergeHeadersSpec) = {
    val newHeaders = getListOfFiles(input.folderPath).sortBy(_.getName).zipWithIndex.map {
      case (headerFile, index) =>
        val headerName = headerFile.getName.split('.').head
        val header = Source.fromFile(headerFile).getLines().next().replaceAll("\"", "")

        val headerItems = header.split(",")

        val newHeaderItems = headerItems.map { columnName =>
          headerName + "-" + columnName
        }

        if (index == 0)
          headerItems.head + "," + newHeaderItems.tail.mkString(",")
        else
          newHeaderItems.tail.mkString(",")
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

  override def inputType = typeOf[MergeHeadersSpec]
}

case class MergeHeadersSpec(folderPath: String)