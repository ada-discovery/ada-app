package runnables.mpower

import javax.inject.Inject

import org.incal.core.runnables.InputFutureRunnable
import org.incal.core.util.seqFutures

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class LinkMDSWithFeatureInfo @Inject()(
    linkFeatureFile: LinkFeatureFileWithFeatureInfo
  ) extends InputFutureRunnable[LinkMDSWithFeatureInfoSpec] {

  private val kMeansTypes = Seq("kMeans", "bisKMeans")
  private val ks = Seq(2, 5, 10, 20)

  override def runAsFuture(input: LinkMDSWithFeatureInfoSpec) = {
    val inputFileNames = fileNames(input.fileNamePrefix + "-scaled")

    seqFutures(inputFileNames) { fileName =>
      val dataSetId = fileName.toLowerCase.substring(0, fileName.size - 4).replaceAllLiterally(".", "_")
      val dataSetName = dataSetId.capitalize.replaceAllLiterally("_", " ").replaceAllLiterally("-", " ")

      val spec = LinkFeatureFileWithFeatureInfoSpec(
        input.featureMetaInfoDataSetId,
        input.scoreDataSetId,
        input.featureFolderName + "/" + fileName,
        input.dataSpacePrefix + "." + dataSetId,
        dataSetName
      )

      linkFeatureFile.runAsFuture(spec)
    }.map(_ => ())
  }

  private def fileNames(prefix: String) =
    for (
      kMeansType <- kMeansTypes;
      k <- ks
    ) yield
      s"$prefix-mds_eigen_unscaled-${kMeansType}_${k}_iter_50.csv"

  override def inputType = typeOf[LinkMDSWithFeatureInfoSpec]
}

case class LinkMDSWithFeatureInfoSpec(
  featureMetaInfoDataSetId: String,
  scoreDataSetId: String,
  featureFolderName: String,
  fileNamePrefix: String,
  dataSpacePrefix: String
)