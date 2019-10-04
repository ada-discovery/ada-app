package runnables.singlecell

import org.incal.core.runnables.InputRunnableExt
import org.incal.core.util.writeStringAsStream

import scala.io.Source

class FindGeneVennOverlaps extends InputRunnableExt[FindGeneVennOverlapsSpec] {

  protected val delimiter = ","
  private val eol = "\n"
  private val header = Seq("x", "y", "color", "gene").mkString(delimiter)

  private val genesOrdered = Seq("aay","brk","Mes2","Doc2","kni","CG14427","ImpL2","ftz","Ilp4","CG8147","tsh","lok","Cyp310a1","Ama","Antp","nub","knrl","run","dpn","Doc3","CG43394","rho","disco","gt","D","fj","sna","Btk29A","Kr","edl","bun","h","CG10479","noc","Mdr49","Blimp-1","twi","apt","trn","toc","htl","dan","eve","zfh1","ImpE2","fkh","MESR3","Ance","tkv","prd","oc","Traf4","srp","danr","odd","croc","E(spl)m5-HLH","CG11208","hb","rau","pxb","Nek2","cnc","hkb","NetA","Dfd","zen","numb","bmm","peb","ken","mfas","bowl","Esp","zen2","tll","cad","gk","exex","erm","CG17786","ems","CenG1A","CG17724")

  private val geneColorMap = genesOrdered.zipWithIndex.toMap

  private val goldenAngle = Math.toRadians(137.5)

  // positions of the sub set centers
  private val centers = Seq(
    (0.5, 3.25),
    (2.5, 3.25),
    (1.5, 1.5),
    (1.5, 3.4),
    (0.85, 2.3),
    (2.15, 2.3),
    (1.5, 2.7)
  )

  override def run(
    input: FindGeneVennOverlapsSpec
  ): Unit = {
    val sub1Genes = readGenes(input.subFileName1, input.cutOff)
    val sub2Genes = readGenes(input.subFileName2, input.cutOff)
    val sub3Genes = readGenes(input.subFileName3, input.cutOff)

    val sub1 = sub1Genes.diff(sub2Genes.union(sub3Genes))
    val sub2 = sub2Genes.diff(sub1Genes.union(sub3Genes))
    val sub3 = sub3Genes.diff(sub1Genes.union(sub2Genes))

    val sub123 = sub1Genes.intersect(sub2Genes).intersect(sub3Genes)

    val sub12 = sub1Genes.intersect(sub2Genes).diff(sub123)
    val sub13 = sub1Genes.intersect(sub3Genes).diff(sub123)
    val sub23 = sub2Genes.intersect(sub3Genes).diff(sub123)

    println("Sub 1 : " + sub1.size)
    println("Sub 2 : " + sub2.size)
    println("Sub 3 : " + sub3.size)

    println("Sub 12 : " + sub12.size)
    println("Sub 13 : " + sub13.size)
    println("Sub 23 : " + sub23.size)

    println("Sub 123: " + sub123.size)

    val contents = Seq(sub1, sub2, sub3, sub12, sub13, sub23, sub123).zipWithIndex.map((createOutput(input.maxRadius)(_, _)).tupled)

    writeStringAsStream(header + eol + contents.mkString(eol), new java.io.File(input.outputFileName))
  }

  private def createOutput(maxRadius: Double)(genes: Set[String], centerIndex: Int) = {
    val (centerX, centerY) = centers(centerIndex)
    val positions = sunflowerPositions(genes.size, centerX, centerY, maxRadius)

    genes.toSeq.sorted.zip(positions).map { case (gene, (x, y)) =>
      val color = geneColorMap.get(gene).getOrElse(throw new RuntimeException(s"Gene $gene not found.")) + 1
      Seq(x, y, color, gene).mkString(delimiter)
    }.mkString(eol)
  }

  protected def readGenes(
    fileName: String,
    cutoff: Int
  ) = {
    val lines = Source.fromFile(fileName).getLines()

    lines.drop(1).map { line =>
      val els = line.split(delimiter, -1).map(_.trim)
      els(2)
    }.toSeq.take(cutoff).toSet
  }

  private def sunflowerPositions(
    pointsCount: Int,
    centerX: Double,
    centerY: Double,
    maxRadius: Double
  ) =
    for (i <- 1 to pointsCount) yield {
      val radius = Math.sqrt(i) * maxRadius / Math.sqrt(pointsCount)
      val angle = i * goldenAngle
      val (x, y) = polarToXY(radius, angle)
      (x + centerX, y + centerY)
    }

  private def polarToXY(
    radius: Double,
    angle: Double
  ) = (radius * Math.sin(angle), radius * Math.cos(angle))
}

class FindGeneVennOverlaps2 extends FindGeneVennOverlaps {

  override protected def readGenes(
    fileName: String,
    cutoff: Int
  ) = {
    val lines = Source.fromFile(fileName).getLines()

    lines.take(1).toSeq.head.split(delimiter, -1).map(_.trim).take(cutoff).toSet
  }
}

case class FindGeneVennOverlapsSpec(
  subFileName1: String,
  subFileName2: String,
  subFileName3: String,
  outputFileName: String,
  cutOff: Int,
  maxRadius: Double
)