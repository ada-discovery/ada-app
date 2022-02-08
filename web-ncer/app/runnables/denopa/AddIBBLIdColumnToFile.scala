package runnables.denopa

import org.ada.server.util.ManageResource.using

import java.io.{File, PrintWriter}
import org.incal.core.runnables.{InputRunnable, InputRunnableExt}

import scala.io.Source

class AddIBBLIdColumnToFile extends InputRunnableExt[AddIBBLIdColumnToFileSpec] {

  private val IBBLDiagId = "-DIAG-VAR-BLD-PLA-"
  private val delimiter = ";"
  private val newIBBLColumnName = "IBBL_ID"

  override def run(input: AddIBBLIdColumnToFileSpec) = {
    using(Source.fromFile(input.fileName)){
      source => {
        val lines = source.getLines()
        val header = lines.next()

        val newLines = lines.map { line =>
          val values = line.split(delimiter, -1)

          val original = values.head
          val originalParts = original.split("-")
          val ibblId = originalParts(0) + IBBLDiagId + originalParts(1)

          (Seq(ibblId, original) ++ values.tail).mkString(delimiter)
        }

        // write to file
        using(new PrintWriter(new File(input.fileName + "_w_ibbl_id"))){
          source => {
            source.write(newIBBLColumnName + delimiter + header + "\n")
            source.write(newLines.mkString("\n"))
          }
        }
      }
    }
  }
}

case class AddIBBLIdColumnToFileSpec(fileName: String)