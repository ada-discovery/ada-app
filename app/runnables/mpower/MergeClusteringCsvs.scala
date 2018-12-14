package runnables.mpower

import org.incal.core.util.{writeStringAsStream, listFiles}

import scala.io.Source

object MergeClusteringCsvs extends App {

  private val delimiter = "\t"
  private val tsneHeader = Seq("subchallengeName", "k", "biskmeans", "perplexity", "pca")
  private val mdsHeader = Seq("subchallengeName", "k", "biskmeans")

  private val folderName = "/home/peter/Research/LCSB/mPower/mPower Challenge/Clustering_pValues/"
//  private val fileName = "mpower_kmeans_tsne_auroc_vs_clazz.csv"

  val csvFiles = listFiles(folderName).filter(_.getName.endsWith("csv"))

  csvFiles.foreach { file =>
    if (file.getName.contains("tsne_"))
      processTSNEFile(file.getName)
    else
      processMDSFile(file.getName)
  }

  private def extractForTSNE(dataSetId: String) = {
    val mainPart = dataSetId.split('.')(1)
    val subchallenge = mainPart.split("-").head

    val perplexityStart = mainPart.indexOf("per-")
    val perplexity = mainPart.substring(perplexityStart + 4, perplexityStart + 6).toInt
    val isBiskmeans  = mainPart.indexOf("biskmeans") > 0
    val kmeansIndex = mainPart.indexOf("kmeans_")
    val k = mainPart.substring(kmeansIndex + 7, kmeansIndex + 9).replaceAllLiterally("_", "").toInt

    val pcaStart = mainPart.indexOf("pca-")

    val pca =
      if (pcaStart > 0)
        Some(mainPart.substring(pcaStart + 4, pcaStart + 6).toInt)
      else
        None

    TSNEClusteringInfo(subchallenge, k, isBiskmeans, perplexity, pca)
  }

  private def extractForMDS(dataSetId: String) = {
    val mainPart = dataSetId.split('.')(1)
    val subchallenge = mainPart.split("-").head

    val isBiskmeans  = mainPart.indexOf("biskmeans") > 0
    val kmeansIndex = mainPart.indexOf("kmeans_")
    val k = mainPart.substring(kmeansIndex + 7, kmeansIndex + 9).replaceAllLiterally("_", "").toInt

    MDSClusteringInfo(subchallenge, k, isBiskmeans)
  }

  def processTSNEFile(fileName: String) = {
    val lines = Source.fromFile(folderName + fileName).getLines()

    val header = lines.next
    val newHeder = (tsneHeader ++ Seq(header)).mkString(delimiter)

    val headerSize = header.split(delimiter, -1).length

    val newLines = lines.map { line =>
      val parts = line.split(delimiter, -1)

      val extraPart = if (parts.length != headerSize) {
        Seq.fill(headerSize - parts.length)("")
      } else
        Nil

      val dataSetId = parts(0)
      val info = extractForTSNE(dataSetId)

      val allItems = Seq(info.subchallengeName, info.k, info.isBiskmeans, info.perplexity, info.pca.map(_.toString).getOrElse(""), line) ++ extraPart
      allItems.mkString(delimiter)
    }

    val newContent = (Seq(newHeder) ++ newLines).mkString("\n")

    writeStringAsStream(newContent, new java.io.File(folderName + "new_" + fileName))
  }

  def processMDSFile(fileName: String) = {
    val lines = Source.fromFile(folderName + fileName).getLines()

    val header = lines.next
    val newHeder = (mdsHeader ++ Seq(header)).mkString(delimiter)

    val headerSize = header.split(delimiter, -1).length

    val newLines = lines.map { line =>
      val parts = line.split(delimiter, -1)

      val extraPart = if (parts.length != headerSize) {
        Seq.fill(headerSize - parts.length)("")
      } else
        Nil

      val dataSetId = parts(0)
      val info = extractForMDS(dataSetId)

      val allItems = Seq(info.subchallengeName, info.k, info.isBiskmeans, line) ++ extraPart
      allItems.mkString(delimiter)
    }

    val newContent = (Seq(newHeder) ++ newLines).mkString("\n")

    writeStringAsStream(newContent, new java.io.File(folderName + "new_" + fileName))
  }

  case class TSNEClusteringInfo(
    subchallengeName: String,
    k: Int,
    isBiskmeans: Boolean,
    perplexity: Int,
    pca: Option[Int]
  )

  case class MDSClusteringInfo(
    subchallengeName: String,
    k: Int,
    isBiskmeans: Boolean
  )
}