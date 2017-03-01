package runnables

import java.io._

import scala.io.Source

object AetionomyLobeFileGnerationScript extends App {
  val sourceFilename = "/home/peter/Data/Aetionomy/Tissue Biomarkers/adni_57normals_BIGR_Tissue_biomarkers_20150730.txt"
  val outputFilename = "/home/peter/Data/Aetionomy/Tissue Biomarkers/adni_57normals_BIGR_Tissue_biomarkers_20150730_by_Lobes.txt"

  object FieldName extends Enumeration {
    val subjectId = Value("subject_id")
//    val icv = Value("icv")
    val lobe = Value("lobe")
    val par = Value("par")
    val csf = Value("csf")
    val gm = Value("gm")
    val wm = Value("wm")
    val wml = Value("wml")
  }

  import FieldName._

  val lines = Source.fromFile(sourceFilename).getLines()

  // new header
  val header = lines.take(1).toSeq.head
  val headerNames = header.split("\t")
  val newHeaderNames = Seq(subjectId, lobe, par, csf, gm, wm, wml).map(_.toString)
  val newHeader = newHeaderNames.mkString("\t")

  // new content
  val newContent = lines.map{ line =>
    val elements = line.split('\t')
    val columnNameValueMap = headerNames.zip(elements).toMap
    val subjectIdValue = columnNameValueMap.get(subjectId.toString).get

    (1 to 10).map { index =>
      def value(field: FieldName.Value) = columnNameValueMap.get(field.toString + index).get

      Seq(
        subjectIdValue,
        index.toString,
        value(par),
        value(csf),
        value(gm),
        value(wm),
        value(wml)
      ).mkString("\t")
    }
  }.flatten.mkString("\n")

  println(newHeader)
  println(newContent)

  // write to file
  val pw = new PrintWriter(new File(outputFilename))
  pw.write(newHeader + "\n")
  pw.write(newContent)
  pw.close
}
