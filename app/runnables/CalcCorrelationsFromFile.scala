package runnables

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Flow, Framing, Sink, Source}
import akka.util.ByteString
import models.Field
import services.stats.StatsService
import util.writeByteArrayStream

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class CalcCorrelationsFromFile @Inject() (statsService: StatsService) extends InputFutureRunnable[CalcCorrelationsFromFileSpec] {

//  private val fileName = "/home/peter/Data/mpower_challenge_submission/all-in-one/all.csv"
//  private val fileName = "/home/peter/Data/mpower_challenge_submission/9641275.csv"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: CalcCorrelationsFromFileSpec) = {
    val headerTailFileSource =
      FileIO.fromPath(Paths.get(input.inputFileName))
        .via(Framing.delimiter(ByteString("\n"), 1000000).map(_.utf8String))
        .prefixAndTail(1)

    val skipFirstColumns = input.skipFirstColumns.getOrElse(0)

    for {
      header <- headerTailFileSource.flatMapConcat { case (header, tail) => Source(header)}.runWith(Sink.head)

      fieldNames = header.split(",").toSeq.drop(skipFirstColumns).map(_.trim)

      corrs <- {
        val source = headerTailFileSource.flatMapConcat { case (header, tail) =>
          //tail.via(doubles)
          tail.map(line => line.split(",").toSeq.drop(skipFirstColumns).map(_.trim.toDouble))
        }
        val featuresNum = fieldNames.size
        statsService.calcPearsonCorrelationsAllDefinedStreamed(source, featuresNum, input.streamParallelism)
      }

    } yield
      exportCorrelationsOptimal(corrs, fieldNames, input.exportFileName)
  }

  private def exportCorrelationsOptimal(
    corrs: Seq[Seq[Option[Double]]],
    fieldNames: Seq[String],
    fileName: String
  ) = {
    val fixedFieldNames = fieldNames.map(_.replaceAllLiterally("u002e", "."))

    val header = "featureName," + fixedFieldNames.mkString(",") + "\n"
    val headerBytes = header.getBytes(StandardCharsets.UTF_8)

    val rowBytesStream = (corrs.toStream, fixedFieldNames).zipped.toStream.map { case (rowCorrs, fieldName) =>
      val rowValues = rowCorrs.map(_.map(_.toString).getOrElse("")).mkString(",")
      val rowContent = fieldName + "," + rowValues + "\n"
      rowContent.getBytes(StandardCharsets.UTF_8)
    }

    val outputStream = Stream(headerBytes) #::: rowBytesStream

    writeByteArrayStream(outputStream, new java.io.File(fileName))
  }

  override def inputType = typeOf[CalcCorrelationsFromFileSpec]
}

case class CalcCorrelationsFromFileSpec(
  inputFileName: String,
  skipFirstColumns: Option[Int],
  streamParallelism: Option[Int],
  exportFileName: String
)