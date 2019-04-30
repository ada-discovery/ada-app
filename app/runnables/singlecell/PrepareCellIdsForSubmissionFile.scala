package runnables.singlecell

import javax.inject.Inject

import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.InputFutureRunnable
import org.incal.core.util.{listFiles, writeStringAsStream}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.io.Source
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class PrepareCellIdsForSubmissionFile @Inject() (
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[PrepareCellIdsForSubmissionFileSpec]
    with PrepareCellIdsForSubmissionHelper {

  private implicit val cellIdFormat = Json.format[CellId]

  override def runAsFuture(
    input: PrepareCellIdsForSubmissionFileSpec
  ): Future[Unit] = {
    val dsa = dsaf(input.cellIdDataSetId).get

    for {
      cellIds <- dsa.dataSetRepo.find().map(_.map(_.as[CellId]))
    } yield
      prepareSubmission(cellIds, input.inputFileName, input.delimiter, input.exportFileName)
  }

  override def inputType = typeOf[PrepareCellIdsForSubmissionFileSpec]
}

class PrepareCellIdsForSubmissionFolder @Inject() (
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[PrepareCellIdsForSubmissionFolderSpec]
    with PrepareCellIdsForSubmissionHelper {

  private implicit val cellIdFormat = Json.format[CellId]

  override def runAsFuture(
    input: PrepareCellIdsForSubmissionFolderSpec
  ): Future[Unit] = {
    val dsa = dsaf(input.cellIdDataSetId).get

    for {
      cellIds <- dsa.dataSetRepo.find().map(_.map(_.as[CellId]))
    } yield {
      val inputFileNames = listFiles(input.inputFolderName).map(_.getName).filter(_.endsWith(input.extension))

      inputFileNames.map { inputFileName =>
        val exportFileBaseName = inputFileName.substring(0, inputFileName.size - (input.extension.size + 1))
        val exportFileName = input.exportFolderName + "/" + exportFileBaseName + s"_ready.csv"

        prepareSubmission(cellIds, input.inputFolderName + "/" + inputFileName, input.delimiter, exportFileName)
      }
    }
  }

  override def inputType = typeOf[PrepareCellIdsForSubmissionFolderSpec]
}

trait PrepareCellIdsForSubmissionHelper {

  private val defaultDelimiter = ","
  protected val eol = "\n"

  private val geneNameReplacements = Map("Blimp_1" -> "Blimp-1", "E_spl_m5_HLH" -> "E(spl)m5-HLH")

  protected def prepareSubmission(
    cellIds: Traversable[CellId],
    inputFileName: String,
    delimiterOption: Option[String],
    exportFileName: String
  ): Unit = {
    val delimiter = StringEscapeUtils.unescapeJava(delimiterOption.getOrElse(defaultDelimiter))

    val cellIdMap = cellIds.map(cellInfo => (cellInfo.Cell, cellInfo.Id)).toMap
    val lines = Source.fromFile(inputFileName).getLines()
    val genes = lines.next.split(delimiter, -1).map(gene => geneNameReplacements.getOrElse(gene, gene)).sorted

    val cellIdPositions = lines.map { line =>
      val elements = line.split(delimiter, 2)
      (cellIdMap.get(elements(0)).get, elements(1))
    }.toSeq.sortBy(_._1)

    val newLines = cellIdPositions.map { case (cellId, positions) => cellId + delimiter + positions }
    val newGeneHeaders = genes.grouped(10).map(group => (Seq("") ++ group.toSeq).mkString(delimiter))

    val content = (newGeneHeaders ++ newLines).mkString(eol)
    writeStringAsStream(content, new java.io.File(exportFileName))
  }
}

case class PrepareCellIdsForSubmissionFileSpec(
  cellIdDataSetId: String,
  inputFileName: String,
  delimiter: Option[String],
  exportFileName: String
)

case class PrepareCellIdsForSubmissionFolderSpec(
  cellIdDataSetId: String,
  inputFolderName: String,
  extension: String,
  delimiter: Option[String],
  exportFolderName: String
)

case class CellId(Cell: String, Id: Int)