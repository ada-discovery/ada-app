package runnables.dl4j

import org.incal.core.InputRunnable

import scala.reflect.runtime.universe.typeOf

class RunDL4JTimeSeriesClassificationWithRandomSplit extends InputRunnable[RunDL4JTimeSeriesClassificationWithRandomSplitSpec] with DL4JHelper {

  override def run(input: RunDL4JTimeSeriesClassificationWithRandomSplitSpec) = {
    val featuresDir = "file:////" + input.featuresBaseDir
    val expectedOutputDir = "file:////" + input.expectedOutputBaseDir

    // training and validation data sets
    val (trainingData, validationData) = TimeSeriesDataSetIterator(
      input.batchSize, input.outputNum, featuresDir, expectedOutputDir, input.startIndex, input.endIndex, input.trainingValidationSplitRatio , 1
    )

    // build a CNN model
    log.info("Building a CNN model....")
    val config = CNN1D(
      input.numRows, input.numColumns, input.outputNum, input.learningRate, input.kernelSize, input.poolingKernelSize, input.convolutionFeaturesNums, input.dropOut, input.lossClassWeights
    )

    // launch and report the results
    launchAndReportResults(config, trainingData, validationData, input.numEpochs, input.outputNum, input.resultsExportDir, input)
  }

  override def inputType = typeOf[RunDL4JTimeSeriesClassificationWithRandomSplitSpec]
}

case class RunDL4JTimeSeriesClassificationWithRandomSplitSpec(
  featuresBaseDir: String,
  expectedOutputBaseDir: String,
  resultsExportDir: String,
  startIndex: Int,
  endIndex: Int,
  trainingValidationSplitRatio: Double,
  numRows: Int,
  numColumns: Int,
  outputNum: Int,
  batchSize: Int,
  numEpochs: Int,
  learningRate: Double,
  kernelSize: Int,
  poolingKernelSize: Int,
  convolutionFeaturesNums: Seq[Int],
  dropOut: Double,
  lossClassWeights: Seq[Double]
)
