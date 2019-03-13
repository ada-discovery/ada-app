package runnables.dl4j

import org.incal.core.InputRunnable

import scala.reflect.runtime.universe.typeOf

class RunDL4JTimeSeriesClassification extends InputRunnable[RunDL4JTimeSeriesClassificationSpec] with DL4JHelper {

  override def run(input: RunDL4JTimeSeriesClassificationSpec) = {
    val featuresDir = "file:////" + input.featuresBaseDir
    val expectedOutputDir = "file:////" + input.expectedOutputBaseDir

    // training data set
    val trainingData = TimeSeriesDataSetIterator(
      input.batchSize, input.outputNum, featuresDir, expectedOutputDir, input.trainingStartIndex, input.trainingEndIndex, 1
    )

    // validation data set
    val validationData = TimeSeriesDataSetIterator(
      input.batchSize, input.outputNum, featuresDir, expectedOutputDir, input.validationStartIndex, input.validationEndIndex, 1
    )

    // build a CNN model
    log.info("Building a CNN model....")
    val config = CNN1D(input.numRows, input.numColumns, input.outputNum, input.learningRate, input.kernelSize, input.poolingKernelSize, input.convolutionFeaturesNums, input.dropOut)

    // launch and report the results
    launchAndReportResults(config, trainingData, validationData, input.numEpochs, input.resultsExportDir, input)
  }

  override def inputType = typeOf[RunDL4JTimeSeriesClassificationSpec]
}

case class RunDL4JTimeSeriesClassificationSpec(
  featuresBaseDir: String,
  expectedOutputBaseDir: String,
  resultsExportDir: String,
  trainingStartIndex: Int,
  trainingEndIndex: Int,
  validationStartIndex: Int,
  validationEndIndex: Int,
  numRows: Int,
  numColumns: Int,
  outputNum: Int,
  batchSize: Int,
  numEpochs: Int,
  learningRate: Double,
  kernelSize: Int,
  poolingKernelSize: Int,
  convolutionFeaturesNums: Seq[Int],
  dropOut: Double
)
