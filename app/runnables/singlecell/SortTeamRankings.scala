package runnables.singlecell

import org.incal.core.runnables.InputRunnableExt
import org.incal.core.util.writeStringAsStream
import org.incal.spark_ml.MLResultUtil

import scala.io.Source

class SortTeamRankings extends InputRunnableExt[SortTeamRankingsSpec] {

  private val delimiter = ","
  private val eol = "\n"

  override def run(
    input: SortTeamRankingsSpec
  ): Unit = {
    val lines = Source.fromFile(input.fileName).getLines()

    val teams = lines.next().split(delimiter, -1).map(_.trim)

    val rankings = lines.map { line =>
      line.split(delimiter, -1).map(_.trim.toDouble)
    }.toSeq

    val teamRankingsSorted = rankings.transpose.zip(teams).map { case (rankings, team) =>
      (MLResultUtil.median(rankings.sorted), (team, rankings))
    }.sortBy(_._1).map(_._2)

    val header = teamRankingsSorted.map(_._1).mkString(delimiter)

    val content = teamRankingsSorted.map(_._2).transpose.map { rankings =>
      rankings.mkString(delimiter)
    }.mkString(eol)

    val outputFileName = if (input.fileName.endsWith("csv"))
      input.fileName.substring(0, input.fileName.size - 4) + "_sorted.csv"
    else
      input.fileName + "_sorted"

    writeStringAsStream(header + eol + content, new java.io.File(outputFileName))
  }
}

case class SortTeamRankingsSpec(fileName: String)