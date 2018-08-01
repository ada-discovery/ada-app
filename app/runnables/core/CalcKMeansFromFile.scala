package runnables.core

import java.nio.file.Paths

import com.banda.core.plotter.Plotter
import com.google.inject.Inject
import models.ml.unsupervised.{BisectingKMeans, KMeans, UnsupervisedLearning}
import org.apache.spark.sql.Row
import org.apache.spark.sql.types._
import org.apache.hadoop.fs._
import play.api.Logger
import runnables.InputRunnable
import services.SparkApp
import services.ml.MachineLearningService

import collection.JavaConverters._
import scala.reflect.runtime.universe.typeOf
import scala.util.Random
import util.writeStringAsStream

class CalcKMeansFromFile @Inject()(
    val sparkApp: SparkApp,
    val machineLearningService: MachineLearningService
  ) extends InputRunnable[CalcKMeansFromFileSpec] with CalcKMeansHelper {

  def run(input: CalcKMeansFromFileSpec) = {
    val model =
      if (input.useBisecting)
        BisectingKMeans(None, k = input.k, maxIteration = input.maxIterations)
      else
        KMeans(None, k = input.k, maxIteration = input.maxIterations)

    val exportPlotFileName = if (input.exportPlot) Some(input.exportFileName + ".png") else None
    calcKMeansAux(input.inputFileName, input.exportFileName, exportPlotFileName, model)
  }

  override def inputType = typeOf[CalcKMeansFromFileSpec]
}

class CalcKMeansFromFolder @Inject()(
    val sparkApp: SparkApp,
    val machineLearningService: MachineLearningService
  ) extends InputRunnable[CalcKMeansFromFolderSpec] with CalcKMeansHelper {

  def run(input: CalcKMeansFromFolderSpec) = {
    val model =
      if (input.useBisecting)
        BisectingKMeans(None, k = input.k, maxIteration = input.maxIterations)
      else
        KMeans(None, k = input.k, maxIteration = input.maxIterations)

    val modelPrefix = if (input.useBisecting) "bisKMeans" else "kMeans"
    val iterPart = input.maxIterations.map(iter => "_iter_" + iter).getOrElse("")

    val inputFileNames = util.getListOfFiles(input.inputFolderName).map(_.getName).filter(_.endsWith(".csv"))

    inputFileNames.map { inputFileName =>
      logger.info(s"Executing k-means with k=${input.k} for the file '$inputFileName'.")
      val exportFileBaseName = inputFileName.substring(0, inputFileName.size - 4) + s"-${modelPrefix}_${input.k}${iterPart}"
      val exportFileName = input.exportFolderName + "/" + exportFileBaseName + ".csv"
      val exportPlotFileName = if (input.exportPlot) Some(input.exportFolderName + "/" + exportFileBaseName + ".png") else None

      calcKMeansAux(input.inputFolderName + "/" + inputFileName, exportFileName, exportPlotFileName, model)
    }
  }

  override def inputType = typeOf[CalcKMeansFromFolderSpec]
}

case class CalcKMeansFromFileSpec(
  k: Int,
  maxIterations: Option[Int],
  useBisecting: Boolean,
  inputFileName: String,
  exportFileName: String,
  exportPlot: Boolean
)

case class CalcKMeansFromFolderSpec(
  k: Int,
  maxIterations: Option[Int],
  useBisecting: Boolean,
  inputFolderName: String,
  exportFolderName: String,
  exportPlot: Boolean
)

trait CalcKMeansHelper {

  val sparkApp: SparkApp
  val machineLearningService: MachineLearningService

  protected val logger = Logger
  private val session = sparkApp.session
  private val fs = FileSystem.get(sparkApp.sc.hadoopConfiguration)

  private val clusterClassColumnName = "clazz"
  private val plotter = Plotter.createExportInstance("svg")

  protected def calcKMeansAux(
    inputFileName: String,
    exportFileName: String,
    exportPlotFileName: Option[String],
    model: UnsupervisedLearning
  ) = {
    val df = session.read.format("csv").option("header", "true").option("inferSchema", "true").load(inputFileName)

    val idColumnName = df.columns(0)

    var idClusters = (0 to 3).foldLeft(Nil: Traversable[(String, Int)]) { case (idClusters, trail) =>
      if (idClusters.isEmpty || idClusters.forall(_._2 == 1)) {
        val newModel = if (trail > 0) {
          logger.error(s"A single cluster obtained! Repeating the clusterization (trail $trail).")
          setCurrentTimeAsSeed(model)
        } else
          model

        machineLearningService.clusterDf(df, idColumnName, newModel, None, None)
      } else
        idClusters
    }

    val rows = idClusters.toList.map(Row.fromTuple).asJava

    val schema = StructType(Seq(
      StructField(idColumnName, StringType, false),
      StructField(clusterClassColumnName, IntegerType, false)
    ))

    val idClustersDf = session.createDataFrame(rows, schema)
    val finalDf = df.join(idClustersDf, Seq(idColumnName))


    // export results to as a csv
    val exportDir = createRandomTempDirName
    finalDf.write.option("header", "true").csv(exportDir)

    val file = fs.globStatus(new Path(exportDir + "/part*"))(0).getPath().getName()

    fs.rename(new Path(exportDir + "/" + file), new Path(exportFileName))
    fs.delete(new Path(exportDir), true)

    // export as a plot
    if (exportPlotFileName.isDefined) {
      val featureColumnNames = df.columns.filterNot(_.equals(idColumnName)).toSeq
      val finalDfRows = finalDf.select(clusterClassColumnName, featureColumnNames: _*).collect()

      val values = finalDfRows.map { row =>
        for (i <- 1 to row.size - 1) yield row.getDouble(i)
      }

      // TODO: use labels
      val labels = finalDfRows.map(_.getInt(0))

      plotter.plotXY(values, "k-Means")
      writeStringAsStream(plotter.getOutput, new java.io.File(exportPlotFileName.get))
    }
  }

  private def setCurrentTimeAsSeed(
    model: UnsupervisedLearning
  ): UnsupervisedLearning =
    model match {
      case kMeans: KMeans => kMeans.copy(seed = Some(new java.util.Date().getTime))
      case bisectingKMeans: BisectingKMeans => bisectingKMeans.copy(seed = Some(new java.util.Date().getTime))
      case _ => model
    }

  private def createRandomTempDirName = {
    val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
    val name = tmpDir.getFileSystem.getPath(Random.nextLong().toString)
    if (name.getParent != null) throw new IllegalArgumentException("Invalid prefix or suffix")
    tmpDir.resolve(name).toString
  }
}