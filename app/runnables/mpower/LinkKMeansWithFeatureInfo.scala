package runnables.mpower

import javax.inject.Inject

import org.incal.core.runnables.InputFutureRunnable
import org.incal.core.util.seqFutures

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class LinkKMeansWithFeatureInfo @Inject()(
    linkFeatureFile: LinkFeatureFileWithFeatureInfo
  ) extends InputFutureRunnable[LinkKMeansWithFeatureInfoSpec] {

//iter 4000, theta 0.25, per 20&50, pca (no, pca 20), scaled & unscaled

  private val perplexities = Seq(20, 50)
  private val kMeansTypes = Seq("kMeans", "bisKMeans")
  private val ks = Seq(2, 5, 10, 20)
  private val pcas = Seq("") // , "_pca-20")

  override def runAsFuture(input: LinkKMeansWithFeatureInfoSpec) = {
    val inputFileNames = fileNames(input.fileNamePrefix) ++ fileNames(input.fileNamePrefix + "-scaled")

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
      per <- perplexities;
      kMeansType <- kMeansTypes;
      k <- ks;
      pca <- pcas
    ) yield
      s"$prefix-cols-2d_iter-4000_per-$per.0_theta-0.25${pca}-${kMeansType}_${k}_iter_50.csv"

  override def inputType = typeOf[LinkKMeansWithFeatureInfoSpec]
}

case class LinkKMeansWithFeatureInfoSpec(
  featureMetaInfoDataSetId: String,
  scoreDataSetId: String,
  featureFolderName: String,
  fileNamePrefix: String,
  dataSpacePrefix: String
)