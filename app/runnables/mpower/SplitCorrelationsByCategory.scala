package runnables.mpower

import java.nio.charset.StandardCharsets
import javax.inject.Inject

import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.InputFutureRunnable
import org.incal.core.util.{GroupMapList, writeByteArrayStream}
import services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import org.ada.server.field.FieldTypeHelper
import org.ada.server.AdaException

import scala.io.Source

class SplitCorrelationsByCategory @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnable[SplitCorrelationsByCategorySpec] {

  private val submissionIdFieldName = "SubmissionId"
  private val featureFieldName = "Name"
  private val categoryFieldName = "Category"

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(input: SplitCorrelationsByCategorySpec) = {
    val metaInfoDsa = dsaf(input.featureMetaInfoDataSetId).get

    for {
      // category field type
      categoryFieldType <- metaInfoDsa.fieldRepo.get(categoryFieldName).map(field =>
        ftf(field.get.fieldTypeSpec)
      )

      // create a submission feature name -> category map
      categorySubmissionFeatureNamesMap <- metaInfoDsa.dataSetRepo.find().map { jsons =>
        jsons.map { json =>
          val submissionId = (json \ submissionIdFieldName).as[Int]
          val featureName = (json \ featureFieldName).as[String]
          val category = categoryFieldType.jsonToDisplayStringOptional(json \ categoryFieldName).getOrElse("undefined")

          (category, submissionId + "-" + featureName)
        }.toGroupMap
      }
    } yield
      categorySubmissionFeatureNamesMap.foreach { case (category, featureNames) =>

        exportCorrelationsToFile(
          input.correlationFileName,
          input.correlationFileName + "_" + category,
          featureNames.toSet
        )
      }
  }

  def exportCorrelationsToFile(
    inputCorrelationFileName: String,
    outputCorrelationFileName: String,
    filterFeatureNames: Set[String]
  ) = {
    val lines = Source.fromFile(inputCorrelationFileName).getLines()

    val header = lines.take(1).toSeq.head
    val headerParts = header.split(",", -1)

    val featureName = headerParts(0)
    val featureNames  = headerParts.drop(1)

    val filteredFeatureNameAndIndeces = featureNames.zipWithIndex.filter{ case (featureName, _) => filterFeatureNames.contains(featureName)}
    val filteredFeatureIndeces = filteredFeatureNameAndIndeces.map(_._2)

    val newLines = lines.flatMap { line =>
      val parts = line.split(",", -1)
      val featureName = parts(0)

      if (filterFeatureNames.contains(featureName)) {
        val featureValues = parts.drop(1)
        val featureValuesCount = featureValues.size
        val filteredFeatureValues = filteredFeatureIndeces.map { index =>
          if (index < featureValuesCount)
            featureValues(index)
          else
            throw new AdaException(s"Index $index is out of bounds. Feature counts: $featureValuesCount.\n Line: $line")
        }
        val newLine = (Seq(featureName) ++ filteredFeatureValues.toSeq).mkString(",")
        Some(newLine + "\n")
      } else
        None
    }

    // new header
    val filteredFeatureNames = filteredFeatureNameAndIndeces.map(_._1)
    val newHeader = (Seq(featureName) ++ filteredFeatureNames).mkString(",") + "\n"
    val headerBytes = newHeader.getBytes(StandardCharsets.UTF_8)

    // new content
    val rowBytesStream = newLines.toStream.map(_.getBytes(StandardCharsets.UTF_8))

    // write the stream to a file
    val outputStream = Stream(headerBytes) #::: rowBytesStream
    writeByteArrayStream(outputStream, new java.io.File(outputCorrelationFileName))
  }

  override def inputType = typeOf[SplitCorrelationsByCategorySpec]
}

case class SplitCorrelationsByCategorySpec(
  featureMetaInfoDataSetId: String,
  correlationFileName: String
)