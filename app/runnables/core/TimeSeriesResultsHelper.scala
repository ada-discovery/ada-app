package runnables.core

import com.banda.core.plotter.{Plotter, SeriesPlotSetting}
import models.ml.RegressionEvalMetric
import play.api.Logger
import services.ml.{MachineLearningUtil, RegressionResultsHolder}
import util.writeStringAsStream

trait TimeSeriesResultsHelper {

  private val plotter = Plotter("svg")
  private val logger = Logger

  protected def exportResults(resultsHolder: RegressionResultsHolder) = {
    // prepare the results stats
    val metricStatsMap = MachineLearningUtil.calcMetricStats(resultsHolder.performanceResults)

    val (trainingScore, testScore, _) = metricStatsMap.get(RegressionEvalMetric.rmse).get

    logger.info("Mean Training RMSE: " + trainingScore.mean)
    logger.info("Mean Test RMSE    : " + testScore.mean)

    resultsHolder.expectedAndActualOutputs.headOption.map { outputs =>
      val trainingOutputs = outputs.head
      val testOutputs = outputs.tail.head

//      println("Training")
//      println(trainingOutputs.map(_._1).mkString(","))
//      println(trainingOutputs.map(_._2).mkString(","))
//
//      println
//      println("Test")
//      println(testOutputs.map(_._1).mkString(","))
//      println(testOutputs.map(_._2).mkString(","))

      exportOutputs(trainingOutputs, "training_io.svg")
      exportOutputs(testOutputs, "test_io.svg")
    }
  }

  private def exportOutputs(
    outputs: Seq[(Double, Double)],
    fileName: String
  ) = {
    val y = outputs.map{ case (yhat, y) => y }
    val yhat = outputs.map{ case (yhat, y) => yhat }

    val output = plotter.plotSeries(
      Seq(y, yhat),
      new SeriesPlotSetting()
        .setXLabel("Time")
        .setYLabel("Value")
        .setCaptions(Seq("Actual Output", "Expected Output"))
    )

    writeStringAsStream(output, new java.io.File(fileName))
  }
}
