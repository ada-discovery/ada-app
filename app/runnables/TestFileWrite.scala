package runnables

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.{util => ju}

import models.Field
import org.apache.commons.io.IOUtils
import play.api.Logger

import scala.reflect.runtime.universe.typeOf
import scala.util.Random
import util.writeByteArrayStream

class TestFileWrite extends InputRunnable[TestFileWriteSpec] {

  private val logger = Logger

  def run(input: TestFileWriteSpec) = {
    val corrsToExport = for (_ <- 1 to input.featureNum) yield for (_ <- 1 to input.featureNum) yield Some(Random.nextDouble())
    val fields = for (i <- 1 to input.featureNum) yield Field(s"feature$i")

    logger.info(s"Exporting the random correlations to ${input.exportFileName} with the method 1.")
    val start1 = new ju.Date
    exportCorrelations(corrsToExport, fields, input.exportFileName)
    logger.info(s"Exporting the random correlations with the method 1 took ${new ju.Date().getTime - start1.getTime} ms.")

    val start2 = new ju.Date
    logger.info(s"Exporting the random correlations to ${input.exportFileName} with the method 2")
    exportCorrelations2(corrsToExport, fields, input.exportFileName + "2")
    logger.info(s"Exporting the random correlations with the method 2 took ${new ju.Date().getTime - start2.getTime} ms.")
  }

  private def exportCorrelations(
    corrs: Seq[Seq[Option[Double]]],
    fields: Seq[Field],
    fileName: String
  ) = {
    val fieldNames = fields.map(field => field.name.replaceAllLiterally("u002e", "."))
    val rows = corrs.zip(fieldNames).map { case (rowCorrs, fieldName) =>
      val rowValues = rowCorrs.map(_.map(_.toString).getOrElse("")).mkString(",")
      fieldName + "," + rowValues
    }

    val header = "featureName," + fieldNames.mkString(",")
    val content = (Seq(header) ++ rows).mkString("\n")

    Files.write(
      Paths.get(fileName),
      content.getBytes(StandardCharsets.UTF_8)
    )
    ()
  }

  private def exportCorrelations2(
    corrs: Seq[Seq[Option[Double]]],
    fields: Seq[Field],
    fileName: String
  ) = {
    val fieldNames = fields.map(field => field.name.replaceAllLiterally("u002e", "."))

    val header = "featureName," + fieldNames.mkString(",") + "\n"
    val headerBytes = header.getBytes(StandardCharsets.UTF_8)

    val rowBytesStream = (corrs.toStream, fieldNames).zipped.toStream.map { case (rowCorrs, fieldName) =>
      val rowValues = rowCorrs.map(_.map(_.toString).getOrElse("")).mkString(",")
      val rowContent = fieldName + "," + rowValues + "\n"
      rowContent.getBytes(StandardCharsets.UTF_8)
    }

    val outputStream = Stream(headerBytes) #::: rowBytesStream

    writeByteArrayStream(outputStream, new java.io.File(fileName))
  }

  override def inputType = typeOf[TestFileWriteSpec]
}

case class TestFileWriteSpec(
  featureNum: Int,
  exportFileName: String
)