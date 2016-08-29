package runnables

import scala.io.Source
import java.io._

object PPMITest extends App {
  val sourceFilename = "/home/peter/Data/PPMI.FINAL.QC.FILTERED.MAF0.005.EXONSPLICING.NOTSYNONYMOUS.vcf.istats"
  val outputFilename = "/home/peter/Data/PPMI.FINAL.QC.FILTERED.MAF0.005.EXONSPLICING.NOTSYNONYMOUS.vcf.istats_w_groups"

  val lines = Source.fromFile(sourceFilename).getLines()

  // new header
  val header = lines.take(1).toSeq.head
  val headerNames = header.split("\t")
  val newHeaderNames = Seq(headerNames.head, "GROUP") ++ headerNames.tail
  val newHeader = newHeaderNames.mkString("\t")

  // new content
  val newContent = lines.map{ line =>
    val elements = line.split('\t')
    val id = elements.head
    val group = if (id.endsWith("PD")) {
      "PD"
    } else if (id.endsWith("HC")) {
      "HC"
    } else
      throw new IllegalArgumentException(id)
    val newElements = Seq(id, group) ++ elements.tail
    newElements.mkString("\t")
  }.mkString("\n")


  println(newHeader)
  println(newContent)

  // write to file
  val pw = new PrintWriter(new File(outputFilename))
  pw.write(newHeader + "\n")
  pw.write(newContent)
  pw.close
}
