package runnables.singlecell

import javax.inject.Inject

import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.runnables.InputFutureRunnable
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.util.writeStringAsStream

import scala.concurrent.Future
import scala.io.Source
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class FilterTargetGeneVarianceFromFile @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnable[FilterTargetGeneVarianceFromFileSpec] {

  private val defaultDelimiter = ","
  private val eol = "\n"

  override def runAsFuture(
    input: FilterTargetGeneVarianceFromFileSpec
  ): Future[Unit] = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))
    val dsa = dsaf(input.targetGenesDataSetId).get

    for {
      genesSet <- dsa.dataSetRepo.find(projection = Seq("Gene")).map(_.map(json => (json \ "Gene").as[String]).toSet)
    } yield {
      val lines = Source.fromFile(input.varianceInputFileName).getLines()

      val header = lines.next()

      val geneVariances = lines.map { line =>
        val els = line.split(delimiter, -1).map(_.trim)
        val geneName = els(0)
        val variance = els(1).toDouble

        (geneName, variance)
      }.toSeq.sortBy(-_._2)

      val newLines = geneVariances.filter { case (geneName, _) => genesSet.contains(geneName) }.map { case (geneName, variance) => geneName + delimiter + variance }
      val content = (Seq(header) ++ newLines).mkString(eol)
      writeStringAsStream(content, new java.io.File(input.exportFileName))
    }
  }

  override def inputType = typeOf[FilterTargetGeneVarianceFromFileSpec]
}

case class FilterTargetGeneVarianceFromFileSpec(
  targetGenesDataSetId: String,
  varianceInputFileName: String,
  delimiter: Option[String],
  exportFileName: String
)