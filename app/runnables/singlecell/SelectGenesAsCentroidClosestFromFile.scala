package runnables.singlecell

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.{InputFutureRunnable, InputRunnable}
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger

import collection.mutable.{ArrayBuffer, Map => MMap, Set => MSet}
import org.incal.core.dataaccess.Criterion._
import util.writeStringAsStream
import util.GroupMapList

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.Random

class SelectGenesAsCentroidClosestFromFile @Inject()(
      dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[SelectGenesAsCentroidClosestFromFileSpec]
    with SelectGenesAsCentroidClosessHelper {

  override def runAsFuture(input: SelectGenesAsCentroidClosestFromFileSpec) = {
    val cellPositionGeneDsa = dsaf(input.cellPositionGeneDataSetId).get

    for {
      // get all the referenced gene names
      targetGenes <- cellPositionGeneDsa.dataSetRepo.find(projection = Seq("Gene")).map(_.map(json => (json \ "Gene").as[String]).toSet)
    } yield {
      selectGenes(targetGenes, input.clusterShuffling, input.inputFileName, input.delimiter, input.exportFileName)
    }
  }

  override def inputType = typeOf[SelectGenesAsCentroidClosestFromFileSpec]
}

class SelectGenesAsCentroidClosestFromFolder @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[SelectGenesAsCentroidClosestFromFolderSpec]
    with SelectGenesAsCentroidClosessHelper {

  override def runAsFuture(input: SelectGenesAsCentroidClosestFromFolderSpec) = {
    val cellPositionGeneDsa = dsaf(input.cellPositionGeneDataSetId).get

    val shuffledNamePart = if (input.clusterShuffling) "_shuffled" else ""

    for {
      // get all the referenced gene names
      targetGenes <- cellPositionGeneDsa.dataSetRepo.find(projection = Seq("Gene")).map(_.map(json => (json \ "Gene").as[String]).toSet)
    } yield {
      val inputFileNames = util.getListOfFiles(input.inputFolderName).map(_.getName).filter(_.endsWith(input.extension))

      inputFileNames.map { inputFileName =>
        val exportFileBaseName = inputFileName.substring(0, inputFileName.size - (input.extension.size + 1))
        val exportFileName = input.exportFolderName + "/" + exportFileBaseName + s"_selected_genes${shuffledNamePart}.csv"

        selectGenes(targetGenes, input.clusterShuffling, input.inputFolderName + "/" + inputFileName, input.delimiter, exportFileName)
      }
    }
  }

  override def inputType = typeOf[SelectGenesAsCentroidClosestFromFolderSpec]
}

trait SelectGenesAsCentroidClosessHelper {

  private val logger = Logger

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()
  private val defaultDelimiter = ","

  private val eol = "\n"

  protected def selectGenes(
    targetGenes: Set[String],
    clusterShuffling: Boolean,
    inputFileName: String,
    delimiterOption: Option[String],
    exportFileName: String
  ): Unit = {
    val delimiter = StringEscapeUtils.unescapeJava(delimiterOption.getOrElse(defaultDelimiter))

    val lines = Source.fromFile(inputFileName).getLines()
    val header = lines.next

    val clusterInfos = lines.map { line =>
      val els = line.split(delimiter, -1).map(_.trim)
      val geneName = els(0)
      val x1 = els(1).toDouble
      val x2 = els(2).toDouble
      val cluster = els(3).toInt

      (cluster, (x1, x2, geneName))
    }.toSeq

    val clusterElements = clusterInfos.toGroupMap
    val targetGeneInfos = clusterInfos.filter(info => targetGenes.contains(info._2._3))

    val clusterCentroids = clusterElements.map { case (clusterId, elements) =>
      val x1s = elements.map(_._1)
      val x2s = elements.map(_._2)

      (clusterId, (x1s.sum / x1s.size, x2s.sum / x2s.size))
    }.toSeq.sortBy(_._1)

    logger.info(s"Selecting ${clusterCentroids.size} genes from the file ${inputFileName}.")

    val availableCellIndeces = ArrayBuffer((0 until targetGeneInfos.size) :_*)

    val shuffledClusterCentroids = if (clusterShuffling) Random.shuffle(clusterCentroids) else clusterCentroids

    val geneDists = shuffledClusterCentroids.map { case (clusterId, (c1, c2)) =>

      val targetGeneDists = availableCellIndeces.map { index =>
        val (_, (x1, x2, geneName)) = targetGeneInfos(index)
        val diff1 = x1 - c1
        val diff2 = x2 - c2
        val dist = Math.sqrt(diff1 * diff1 +  diff2 * diff2)
        (index, geneName, dist)
      }

      val (index, geneName, dist) = targetGeneDists.minBy(_._3)

      availableCellIndeces.-=(index)

      (geneName, dist)
    }

    val selectedGenes = geneDists.map { case (geneName, dist) => geneName }.sorted.mkString(delimiter)
    val meanDistance = geneDists.map { case (_, dist) => dist }.sum / geneDists.size

    // export the gene selections
    writeStringAsStream(selectedGenes, new java.io.File(exportFileName))

    // export the meta infos
    val metaInfoContent = "Mean Distance: " + meanDistance
    writeStringAsStream(metaInfoContent, new java.io.File(exportFileName + "_meta"))
  }
}

case class SelectGenesAsCentroidClosestFromFileSpec(
  cellPositionGeneDataSetId: String,
  clusterShuffling: Boolean,
  inputFileName: String,
  delimiter: Option[String],
  exportFileName: String
)

case class SelectGenesAsCentroidClosestFromFolderSpec(
  cellPositionGeneDataSetId: String,
  clusterShuffling: Boolean,
  inputFolderName: String,
  extension: String,
  delimiter: Option[String],
  exportFolderName: String
)