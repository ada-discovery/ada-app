package runnables

import java.util

import org.datavec.api.records.reader.impl.csv.{CSVRecordReader, CSVSequenceRecordReader}
import org.datavec.api.split.NumberedFileInputSplit
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator.AlignmentMode
import org.deeplearning4j.eval.Evaluation
import org.deeplearning4j.nn.api.Updater
import org.deeplearning4j.nn.conf.inputs.InputType
import org.deeplearning4j.nn.conf.layers._
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToCnnPreProcessor
import org.deeplearning4j.nn.conf.{ComputationGraphConfiguration, ConvolutionMode, InputPreProcessor, NeuralNetConfiguration}
import org.deeplearning4j.nn.graph.ComputationGraph
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
//import org.deeplearning4j.ui.api.UIServer
//import org.deeplearning4j.ui.stats.StatsListener
//import org.deeplearning4j.ui.storage.InMemoryStatsStorage
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.dataset.api.iterator.{DataSetIterator, MultiDataSetIterator}
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.learning.config.{Adam, Nesterovs}
import org.nd4j.linalg.lossfunctions.LossFunctions
import org.nd4j.linalg.lossfunctions.impl.{LossBinaryXENT, LossMCXENT}
import org.slf4j.{Logger, LoggerFactory}

object MPowerRotationToDiagnosis2DConvClassification extends App {

  private val NB_TRAIN_EXAMPLES = 8000   // number of training examples
  private val NB_TEST_EXAMPLES = 800     // number of testing examples

  private val path = "file:////home/peter/Data/MJFF_grant_data/mpower_challenge_dl4j/"  // set parent directory
  private val featureBaseDir = path + "features"                                        // set feature directory
  private val outputBaseDir = path + "professional-diagnosis"                           // set label directory

  private val log: Logger = LoggerFactory.getLogger(MPowerRotationToDiagnosis2DConvClassification.getClass)

  execute

  @throws[Exception]
  def execute = {

    // Number of input channels
    val nChannels = 3

    val timeSeriesLength = 4000

    // The number of possible outcomes
    val outputNum: Int = 2     // PD vs Controls

    // Test batch size
    val batchSize: Int = 4

    // Number of training epochs
    val nEpochs: Int = 5

    // seed
    val seed: Int = 123

    // Create an iterator using the batch size for one iteration

    log.info("Load data....")
    val (trainIterator, testIterator) = dataIterators(batchSize)

    // Construct the neural network

    log.info("Build model....")

    // learning rate schedule in the form of <Iteration #, Learning Rate>
//    val lrSchedule: java.util.Map[Integer, Double] = new java.util.HashMap[Integer, Double]
//    lrSchedule.put(0, 0.01)
//    lrSchedule.put(1000, 0.005)
//    lrSchedule.put(3000, 0.001)

    //nIn and nOut specify depth. nIn here is the nChannels and nOut is the number of filters to be applied

    def convolution(
      kernelSize1: Int,
      kernelSize2: Int,
      out: Int
    ) =
      new ConvolutionLayer.Builder(kernelSize1, kernelSize2)         // sliding window (kernel) size
      .stride(1, nChannels)                                          // we move by one
      .nOut(out)                                                     // the depth
      .build()

    def maxPooling(
      kernelSize1: Int,
      kernelSize2: Int
    ) =
      new SubsamplingLayer.Builder(PoolingType.MAX)    // max-pooling
        .kernelSize(kernelSize1, kernelSize2)
        .stride(1, 1)                                  // we move by one
        .build()


    //    Input
    //    -> Convolution: number = 8, filter size = 5 -> Max pool
    //      -> Convolution: n = 16, s = 5 -> Max pool
    //      -> Convolution: n = 32, s = 4 -> Max pool
    //      -> Convolution: n = 32, s = 4 -> Max pool
    //      -> Convolution: n = 64, s = 4 -> Max pool
    //      -> Convolution: n = 64, s = 4 -> Max pool
    //      -> Convolution: n =128, s = 4 -> Max pool
    //      -> Convolution: n =128, s = 5 -> Max pool
    //      -> Sigmoid activation;
    //      .convolutionMode(ConvolutionMode.Same) //This is important so we can 'stack' the results later

    val config: ComputationGraphConfiguration = new NeuralNetConfiguration.Builder()
      .seed(seed)
      .l2(0.0005)
      .weightInit(WeightInit.XAVIER)
      .activation(Activation.LEAKYRELU)
      .updater(new Nesterovs(0.0005, 0.9))
//      .updater(new Adam(0.0005))
      .convolutionMode(ConvolutionMode.Strict)
      .graphBuilder
      .addInputs("input")
      .layer(0, convolution(5, nChannels, 8), "input")
      .layer(1, maxPooling(5, 1), "0")
      .layer(2, convolution(5, 1, 16), "1")
      .layer(3, maxPooling(5, 1), "2")
      .layer(4, convolution(4, 1, 32), "3")
      .layer(5, maxPooling(4, 1), "4")
      .layer(6, convolution(4, 1, 32), "5")
      .layer(7, maxPooling(4, 1), "6")
      .layer(8, convolution(4, 1, 64), "7")
      .layer(9, maxPooling(4, 1), "8")
      .layer(10, convolution(4, 1, 64), "9")
      .layer(11, maxPooling(5, 1), "10")
      .layer(12, convolution(4, 1, 128), "11")
      .layer(13, maxPooling(4, 1), "12")
      .layer(14, convolution(5, 1, 128), "13")
      .layer(15, maxPooling(5, 1), "14")
//      .layer(16, new GlobalPoolingLayer.Builder()
////        .dropOut(0.5)
//        .poolingType(PoolingType.MAX).build, "15")
//      .layer(17, new DenseLayer.Builder()
//        .nIn(128)
//        .activation(Activation.RELU)
//        .nOut(128).build(), "16")
//      .layer(16, new DenseLayer.Builder()
//        .activation(Activation.RELU)
//        .nOut(128).build(), "15")
      .layer("output", new OutputLayer.Builder(new LossBinaryXENT(Nd4j.create(Array[Double](1, 0.5)))) // LossFunctions.LossFunction.XENT)
        .nOut(outputNum)
        .activation(Activation.SIGMOID)
        .dropOut(0.5)
        .build(), "15")
      .setOutputs("output")
      .setInputTypes(InputType.convolutional(timeSeriesLength, nChannels, 1)) // height, width, depth
      .build()

    // [miniBatchSize, channels (depth) , height, width]

//  * (a) Reshape 3d activations out of RNN layer, with shape [miniBatchSize, numChannels*inputHeight*inputWidth, timeSeriesLength])
//    * into 4d (CNN) activations (with shape [numExamples*timeSeriesLength, numChannels, inputWidth, inputHeight]) <br>





//      .inputPreProcessor(8, new RnnToFeedForwardPreProcessor())
//      .setInputType(new InputTypeConvolutional1D(64, nChannels))
    // Construct and initialize model
    val model: ComputationGraph = new ComputationGraph(config)
    model.init()

    log.info("Train model....")
//    model.setListeners(new ScoreIterationListener(10)) //Print score every 10 iterations

//    //Initialize the user interface backend
//    val uiServer = UIServer.getInstance()
//
//    //Configure where the network information (gradients, score vs. time etc) is to be stored. Here: store in memory.
//    val statsStorage = new InMemoryStatsStorage()         //Alternative: new FileStatsStorage(File), for saving and loading later
//
//    //Attach the StatsStorage instance to the UI: this allows the contents of the StatsStorage to be visualized
//    uiServer.attach(statsStorage);
//
//    //Then add the StatsListener to collect this information from the network, as it trains
//    model.setListeners(new StatsListener(statsStorage), new ScoreIterationListener(10))

    for(i <- 0 to nEpochs) {
      model.fit(trainIterator)
      log.info("*** Completed epoch {} ***", i)

      log.info("Evaluate model....")
      val eval: Evaluation = model.evaluate(testIterator)
      log.info(eval.stats())

      testIterator.reset()
    }

    log.info("****************Example finished********************")
  }

  private def dataIterators(batchSize: Int): (DataSetIterator, DataSetIterator) = {
    val trainFeatures = new CSVSequenceRecordReader(1, ",")
    trainFeatures.initialize(new NumberedFileInputSplit(featureBaseDir + "/%d.csv", 0, NB_TRAIN_EXAMPLES - 1))

    val trainLabels = new CSVRecordReader()
    trainLabels.initialize(new NumberedFileInputSplit(outputBaseDir + "/%d.csv", 0, NB_TRAIN_EXAMPLES - 1))

    val trainData = TimeSeriesDataSetIterator(batchSize, trainFeatures, trainLabels)

    // Load testing data
    val testFeatures = new CSVSequenceRecordReader(1, ",")
    testFeatures.initialize(new NumberedFileInputSplit(featureBaseDir + "/%d.csv", NB_TRAIN_EXAMPLES, NB_TRAIN_EXAMPLES + NB_TEST_EXAMPLES))

    val testLabels = new CSVRecordReader()
    testLabels.initialize(new NumberedFileInputSplit(outputBaseDir + "/%d.csv", NB_TRAIN_EXAMPLES, NB_TRAIN_EXAMPLES  + NB_TEST_EXAMPLES))

    val testData = TimeSeriesDataSetIterator(batchSize, testFeatures, testLabels)

    (trainData, testData)
  }
}