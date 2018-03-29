package runnables.core

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.util.ByteString
import util.writeByteArrayStream
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object FeatureMatrixIO {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  def saveSquare[T](
    matrix: Traversable[Seq[T]],
    fieldNames: Seq[String],
    fileName: String,
    asString: T => String
  ) = save[T](matrix, fieldNames, fieldNames, "featureName",  fileName, asString)

  def save[T](
    matrix: Traversable[Seq[T]],
    rowNames: Seq[String],
    columnNames: Seq[String],
    idColumnName: String,
    fileName: String,
    asString: T => String
  ): Unit = {
    val fixedColumnNames = columnNames.map(_.replaceAllLiterally("u002e", "."))
    val fixedRowNames = rowNames.map(_.replaceAllLiterally("u002e", "."))

    val header = idColumnName + "," + fixedColumnNames.mkString(",") + "\n"
    val headerBytes = header.getBytes(StandardCharsets.UTF_8)

    val rowBytesStream = (matrix.toStream, fixedRowNames).zipped.toStream.map { case (row, name) =>
      val rowValues = row.map(asString).mkString(",")
      val rowContent = name + "," + rowValues + "\n"
      rowContent.getBytes(StandardCharsets.UTF_8)
    }

    val outputStream = Stream(headerBytes) #::: rowBytesStream

    writeByteArrayStream(outputStream, new java.io.File(fileName))
  }

  def savePlain[T](
    matrix: Traversable[Seq[T]],
    columnNames: Seq[String],
    fileName: String,
    asString: T => String
  ): Unit = {
    val fixedColumnNames = columnNames.map(_.replaceAllLiterally("u002e", "."))

    val header = fixedColumnNames.mkString(",") + "\n"
    val headerBytes = header.getBytes(StandardCharsets.UTF_8)

    val rowBytesStream = matrix.toStream.map {row =>
      val rowValues = row.map(asString).mkString(",")
      val rowContent = rowValues + "\n"
      rowContent.getBytes(StandardCharsets.UTF_8)
    }

    val outputStream = Stream(headerBytes) #::: rowBytesStream

    writeByteArrayStream(outputStream, new java.io.File(fileName))
  }

  def load(
    fileName: String,
    skipFirstColumnsOption: Option[Int]
  ): Future[(Source[Seq[Double], _], Seq[String])] = {
    val headerTailFileSource =
      FileIO.fromPath(Paths.get(fileName))
        .via(Framing.delimiter(ByteString("\n"), 1000000).map(_.utf8String))
        .prefixAndTail(1)

    val skipFirstColumns = skipFirstColumnsOption.getOrElse(0)

    for {
      header <- headerTailFileSource.flatMapConcat { case (header, tail) => Source(header) }.runWith(Sink.head)
    } yield {
      val fieldNames = header.split(",").toSeq.drop(skipFirstColumns).map(_.trim)

      val source = headerTailFileSource.flatMapConcat { case (header, tail) =>
        //tail.via(doubles)
        tail.map(line => line.split(",").toSeq.drop(skipFirstColumns).map(_.trim.toDouble))
      }
      (source, fieldNames)
    }
  }
}