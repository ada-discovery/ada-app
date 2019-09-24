package runnables.singlecell

import org.incal.core.runnables.InputRunnableExt
import org.incal.core.util.writeStringAsStream

import scala.io.Source

class CalcGeneFrequencies extends InputRunnableExt[CalcGeneFrequenciesSpec] {

  private val delimiter = ","
  private val eol = "\n"
  private val header = Seq("pos", "color", "gene", "freq").mkString(delimiter)
  private val genesOrdered = Seq("aay","brk","Mes2","Doc2","kni","CG14427","ImpL2","ftz","Ilp4","CG8147","tsh","lok","Cyp310a1","Ama","Antp","nub","knrl","run","dpn","Doc3","CG43394","rho","disco","gt","D","fj","sna","Btk29A","Kr","edl","bun","h","CG10479","noc","Mdr49","Blimp-1","twi","apt","trn","toc","htl","dan","eve","zfh1","ImpE2","fkh","MESR3","Ance","tkv","prd","oc","Traf4","srp","danr","odd","croc","E(spl)m5-HLH","CG11208","hb","rau","pxb","Nek2","cnc","hkb","NetA","Dfd","zen","numb","bmm","peb","ken","mfas","bowl","Esp","zen2","tll","cad","gk","exex","erm","CG17786","ems","CenG1A","CG17724")

  private val geneColorMap = genesOrdered.zipWithIndex.toMap

  override def run(
    input: CalcGeneFrequenciesSpec
  ): Unit = {
    val lines = Source.fromFile(input.fileName).getLines()

    val teamGenes = lines.map { line =>
      val els = line.split(delimiter, -1).map(_.trim)
      (els(0), els(1))
    }.toSeq

    val teamsCount = teamGenes.map(_._1).toSet.size

    println("Teams: " + teamsCount)

    val geneFrequencies = teamGenes.map(_._2).groupBy(identity).map { case (gene, items) =>
      (gene, items.size.toDouble / (input.repetitions * teamsCount))
    }

    val content = geneFrequencies.toSeq.sortBy(- _._2).zipWithIndex.map { case ((gene, freq), index) =>
      Seq((index + 1), geneColorMap.get(gene).get + 1, gene, freq).mkString(delimiter)
    }.mkString(eol)

    writeStringAsStream(header + eol + content, new java.io.File(input.fileName + "_freq"))
  }
}

case class CalcGeneFrequenciesSpec(
  fileName: String,
  repetitions: Int
)