package runnables.denopa

import javax.inject.Inject

import dataaccess.JsonUtil
import persistence.dataset.DataSetAccessorFactory
import runnables.FutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

class DeNoPaFieldLabelImport @Inject() (dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val dataSetId = "denopa.clinical_baseline"
  private val inputFile = "/home/peter/Data/DeNoPa/DeNoPa_all_v2.csv"
  private val delimiter = ";"

  override def runAsFuture: Future[Unit] = {
    val fieldRepo = dsaf(dataSetId).get.fieldRepo

    for {
      fields <- fieldRepo.find()
    } yield {
      val fieldNameSet = fields.map(_.name).toSet
      val fileFieldNameAndLabels = readNameAndLabelsFromFile
      val fileFieldNameSet = fileFieldNameAndLabels.map(_._1).toSet

      println()
      println("The number of fields in the file                            : " + fileFieldNameSet.size)
      println("The number of fields in the dataset                         : " + fields.size)
      println("The number of fields in both, the file and the dataset      : " + (fieldNameSet.intersect(fileFieldNameSet).size))
      println("--------------------------------------------------------------")

      val fileDiff = fileFieldNameSet.diff(fieldNameSet)
      println("The number of fields in the file but not NOT in the dataset : " + fileDiff.size)
      println(">>>")
      println(fileDiff.map(JsonUtil.unescapeKey(_)).toSeq.sorted.take(50).mkString("\n"))
      println("<<<")

      val fieldDiff = fieldNameSet.diff(fileFieldNameSet)
      println("The number of fields in the dataset but not NOT in the file : " + fieldDiff.size)
      println(">>>")
      println(fieldDiff.map(JsonUtil.unescapeKey(_)).toSeq.sorted.take(80).mkString("\n"))
      println("<<<")
      println()
    }
  }

  private def readNameAndLabelsFromFile = {
    // Exam;group;label;sub group;original field name;associated question;type;values;note

    val lines = Source.fromFile(inputFile).getLines()
    lines.flatMap { line =>
      val values = line.split(delimiter, -1)

      def value(index: Int) =
        if (index < values.length) {
          val string = values(index).trim
          if (string.isEmpty) None else Some(string)
        } else
          None

      val group = value(1)
      val label = value(2)
      val name = value(4).map(JsonUtil.escapeKey(_))
      (name, label).zipped.headOption
    }
  }
}
