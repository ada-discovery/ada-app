package runnables.mpower

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.incal.core.runnables.InputFutureRunnable
import org.incal.core.util.writeStringAsStream
import org.ada.server.akka.AkkaStreamUtil.fileHeaderAndContentSource

import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class PrepareTimeSeriesForDL4J extends InputFutureRunnable[PrepareTimeSeriesForDL4JSpec] {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: PrepareTimeSeriesForDL4JSpec) =
    process(input)

  def process(
    input: PrepareTimeSeriesForDL4JSpec,
    delimiter: String = "\t",
    seriesDelimiter: String = ","
  ): Future[Unit] = {
    var fileIndex = input.startingFileIndex.getOrElse(0)
    def writeAux(content: String, folderName: String, fileName: String) = {
      val folder = input.outputFolderName + "/" + folderName
      val path = Paths.get(folder)

      if (!Files.exists(path)) {
        Files.createDirectory(path)
      }
      writeStringAsStream(content, new java.io.File(s"$folder/$fileName"))
    }

    for {
      (header, contentSource) <- fileHeaderAndContentSource(input.inputFileName)

      columnNames = header.split(delimiter, -1).map(_.trim)
      seriesHeader = input.inputSeriesIndeces.map(columnNames(_))
      outputColumnName = columnNames(input.outputIndex)

      _ <- contentSource.runForeach { line =>
        val elements = line.split(delimiter, -1).map(_.trim)

        val seriesValues: Seq[Option[Seq[Double]]] = input.inputSeriesIndeces.map { i =>
          val seriesAsString = elements(i).replaceAllLiterally("[", "").replaceAllLiterally("]", "")
          val values = seriesAsString.split(seriesDelimiter, -1).toSeq

          if (values.forall(_.nonEmpty) && values.size > input.minSeriesLength) {
            val padding = Seq.fill(input.targetSeriesLength - values.size)(0d)
            Some(values.map(_.toDouble) ++ padding)
          } else
            None
        }

        val outputValue = if (elements(input.outputIndex).toBoolean) 1 else 0

        if (seriesValues.forall(_.isDefined)) {
          val seriesValuesTran = seriesValues.flatten.transpose
          val seriesContent = (Seq(seriesHeader.mkString(",")) ++ seriesValuesTran.map(_.mkString(","))).mkString("\n")

          println(fileIndex + " size: " + seriesValuesTran.size + " -> " + outputValue)

          writeAux(seriesContent, "features", fileIndex + ".csv")
          writeAux(outputValue.toString, outputColumnName, fileIndex + ".csv")
          fileIndex += 1
        } else {
          println("Empty String!!!!")
        }
      }
    } yield
      ()
  }

  override def inputType = typeOf[PrepareTimeSeriesForDL4JSpec]
}

case class PrepareTimeSeriesForDL4JSpec(
  inputFileName: String,
  inputSeriesIndeces: Seq[Int],
  minSeriesLength: Int,
  targetSeriesLength: Int,
  outputIndex: Int,
  outputFolderName: String,
  startingFileIndex: Option[Int]
)
