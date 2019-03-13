package runnables.dl4j

import org.deeplearning4j.eval.Evaluation
import org.deeplearning4j.nn.graph.ComputationGraph
import play.api.Logger

object CNN_WISDMExample {

  private val log = Logger
  private val path = "file://///home/peter/Data/ML/WISDM_ar_v1.1/WISDM_DL4J/" // set parent directory
  private val featureBaseDir = path + "features" // set feature directory
  private val outputBaseDir = path + "activity" // set label directory

  // Number of training and validation examples:            // user 0 - 28: 0 - 20218
  private val NB_TRAIN_LAST_EXAMPLE = 20218 // try also: 16694, total: 26559
  private val NB_VALID_LAST_EXAMPLE = 26558 // 6584

  @throws[Exception]
  def main(args: Array[String]): Unit = {
    val numRows = 80      // time series length
    val numColumns = 3    // number of features
    val outputNum = 6     // number of output classes
    val batchSize = 400   // batch size for each epoch
//        int rngSeed = 123;         // random number seed for reproducibility

    val numEpochs = 50    // number of epochs to perform
    val rate = 0.001      // learning rate

    // Training data set
    val trainData = TimeSeriesDataSetIterator(batchSize, outputNum, featureBaseDir, outputBaseDir, 0, NB_TRAIN_LAST_EXAMPLE, 1)

    // Validation data set
    val validData = TimeSeriesDataSetIterator(batchSize, outputNum, featureBaseDir, outputBaseDir, NB_TRAIN_LAST_EXAMPLE + 1, NB_VALID_LAST_EXAMPLE, 1)

    // Input shape: [batch,         1,  numRows, numColumns]
    //              [batch, channels,    height,      width]

    log.info("Build model....")
    val config = CNN1D(numRows, numColumns, outputNum, rate, 10, 3, Seq(100, 160), 0.5)

//    println(config.toJson)

    val model = new ComputationGraph(config)
    model.init()

    log.info("Train model....")
//        model.setListeners(new ScoreIterationListener(10)); //Print score every 10 iterations

    for (i <- 0 until numEpochs) {
      model.fit(trainData)
      log.info(s"*** Completed epoch {$i} ***")
//            log.info("Evaluate model (train data)....");
      val trainEval: Evaluation = model.evaluate(trainData)
      log.info("Train accuracy     : " + trainEval.accuracy)
//            log.info("Evaluate model (validation data)....");
      val validEval: Evaluation = model.evaluate(validData)
//            log.info(validEval.stats());
      log.info("Validation accuracy: " + validEval.accuracy)

      trainData.reset()
      validData.reset()
    }
    log.info("****************Example finished********************")
  }
}