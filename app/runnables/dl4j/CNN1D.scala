package runnables.dl4j

import org.deeplearning4j.nn.api.OptimizationAlgorithm
import org.deeplearning4j.nn.conf.{ComputationGraphConfiguration, ConvolutionMode, NeuralNetConfiguration}
import org.deeplearning4j.nn.conf.inputs.InputType
import org.deeplearning4j.nn.conf.layers._
import org.deeplearning4j.nn.weights.WeightInit
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.learning.config.Adam
import org.nd4j.linalg.lossfunctions.LossFunctions
import org.nd4j.linalg.lossfunctions.impl.{LossBinaryXENT, LossMCXENT}

object CNN1D {

  def apply(
    numRows: Int,
    numColumns: Int,
    outputNum: Int,
    learningRate: Double,
    kernelSize: Int,
    poolingKernelSize: Int,
    convolutionFeaturesNums: Seq[Int],
    dropOut: Double,
    lossClassWeights: Seq[Double] = Nil
  ): ComputationGraphConfiguration = {

    def convolution(out: Int, channels: Int) =
      new ConvolutionLayer.Builder(kernelSize, channels)
        .stride(1, 1)
        .nOut(out)
        .activation(Activation.RELU)
        .build()

    def maxPoolingType =
      new SubsamplingLayer.Builder(PoolingType.MAX)
        .kernelSize(poolingKernelSize, 1)
        .stride(poolingKernelSize, 1)
        .build()

    // Input shape: [batch,         1,  numRows, numColumns]
    //              [batch, channels,    height,      width]

    // basic learning setting
    val conf = new NeuralNetConfiguration.Builder()
      .weightInit(WeightInit.XAVIER)
      //            .updater(new Nesterovs(rate, 0.9))
      .updater(new Adam(learningRate))
      .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
      .convolutionMode(ConvolutionMode.Truncate)
      .graphBuilder()
      .addInputs("0")

    val cnnLayersNum = convolutionFeaturesNums.size

    // convolution + max pooling layers
    convolutionFeaturesNums.zipWithIndex.foreach { case (featuresNum, index) =>
      val initChannels = if (index == 0) numColumns else 1

      conf
        .layer(3 * index + 1, convolution(featuresNum, initChannels), (3 * index).toString)
        .layer(3 * index + 2, convolution(featuresNum, 1), (3 * index + 1).toString)

      if (index < cnnLayersNum - 1) {
        conf.layer(3 * index + 3, maxPoolingType, (3 * index + 2).toString)
      }
    }

    // global average pooling
    conf
      .layer(3 * cnnLayersNum, new GlobalPoolingLayer.Builder()
        .poolingType(PoolingType.AVG)
        .build(), (3 * cnnLayersNum - 1).toString)

//      .layer(3 * cnnLayersNum + 1, new DropoutLayer.Builder(0.5)
//        .build(), (3 * cnnLayersNum).toString)

    // final (dense) output layer + dropout
    val lossFunction = if (outputNum == 2)
      if (lossClassWeights.nonEmpty)
        new LossBinaryXENT(Nd4j.create(lossClassWeights.toArray))
          else
        new LossBinaryXENT()
    else
      if (lossClassWeights.nonEmpty)
        new LossMCXENT(Nd4j.create(lossClassWeights.toArray))
      else
        new LossMCXENT()

    LossFunctions.LossFunction.MCXENT

    val activation = if (outputNum == 2) Activation.SIGMOID else Activation.SOFTMAX

    conf
      .layer("output", new OutputLayer.Builder(lossFunction)
        .dropOut(dropOut)
        .nOut(outputNum)
        .activation(activation)
        .build(), (3 * cnnLayersNum).toString)
      .setOutputs("output")
      .setInputTypes(InputType.convolutional(numRows, numColumns, 1)) // height, width, depth
      .build()
  }
}