package runnables.core

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.NotUsed
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
  ): Future[(Source[Seq[Double], _], Seq[String])] =
    for {
      (header, contentSource) <- createHeaderAndContentFileSource(fileName)
    } yield {
      val skipFirstColumns = skipFirstColumnsOption.getOrElse(0)
      val fieldNames = header.split(",", -1).toSeq.drop(skipFirstColumns).map(_.trim)

      val source = contentSource.map { line =>
        line.split(",", -1).toSeq.drop(skipFirstColumns).map(_.trim.toDouble)
      }
      (source, fieldNames)
    }

  def loadArray(
    fileName: String,
    skipFirstColumnsOption: Option[Int]
  ): Future[(Source[Array[Double], _], Seq[String])] =
    for {
      (header, contentSource) <- createHeaderAndContentFileSource(fileName)
    } yield {
      val skipFirstColumns = skipFirstColumnsOption.getOrElse(0)
      val fieldNames = header.split(",", -1).toSeq.drop(skipFirstColumns).map(_.trim)

      val source = contentSource.map { line =>
        line.split(",", -1).drop(skipFirstColumns).map(_.trim.toDouble)
      }
      (source, fieldNames)
    }

  def loadArrayWithFirstIdColumn(
    fileName: String
  ): Future[(Source[(String, Array[Double]), _], Seq[String])] =
    for {
      (header, contentSource) <- createHeaderAndContentFileSource(fileName)
    } yield {
      val fieldNames = header.split(",", -1).map(_.trim)

      val source = contentSource.map { line =>
        val parts = line.split(",", -1)
        (parts(0).trim, parts.tail.map(_.trim.toDouble))
      }
      (source, fieldNames)
    }

  private def createHeaderAndContentFileSource(
    fileName: String
  ): Future[(String, Source[String, _])] = {
    val headerTailSource = FileIO.fromPath(Paths.get(fileName))
      .via(Framing.delimiter(ByteString("\n"), 1000000).map(_.utf8String))
      .prefixAndTail(1)

    val headerSource = headerTailSource.flatMapConcat { case (header, _) => Source(header) }
    val contentSource = headerTailSource.flatMapConcat { case (_, tail) => tail }

    headerSource.runWith(Sink.head).map ( header =>
      (header, contentSource)
    )
  }
}