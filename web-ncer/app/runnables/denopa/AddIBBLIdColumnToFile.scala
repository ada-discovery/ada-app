package runnables.denopa

import java.io.{File, PrintWriter}

import org.incal.core.runnables.{InputRunnable, InputRunnableExt}

import scala.io.Source

class AddIBBLIdColumnToFile extends InputRunnableExt[AddIBBLIdColumnToFileSpec] {

  private val IBBLDiagId = "-DIAG-VAR-BLD-PLA-"
  private val delimiter = ";"
  private val newIBBLColumnName = "IBBL_ID"

  override def run(input: AddIBBLIdColumnToFileSpec) = {
    val lines = Source.fromFile(input.fileName).getLines()
    val header = lines.next()

    val newLines = lines.map { line =>
      val values = line.split(delimiter, -1)

      val original = values.head
      val originalParts = original.split("-")
      val ibblId = originalParts(0) + IBBLDiagId + originalParts(1)

      (Seq(ibblId, original) ++ values.tail).mkString(delimiter)
    }

    // write to file
    val pw = new PrintWriter(new File(input.fileName + "_w_ibbl_id"))
    pw.write(newIBBLColumnName + delimiter + header + "\n")
    pw.write(newLines.mkString("\n"))
    pw.close
  }
}

case class AddIBBLIdColumnToFileSpec(fileName: String)