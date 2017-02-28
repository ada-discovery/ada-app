package runnables

import javax.inject.Inject

import models.ml._
import org.apache.spark.ml.classification.{GBTClassifier, MultilayerPerceptronClassifier, NaiveBayesModel, RandomForestClassifier}
import org.apache.spark.ml.regression._
import services.MachineLearningService

class MachineLearningTest @Inject() (machineLearningService: MachineLearningService) extends Runnable {

//  private val dataSetId = "ppmi.ppmi_si"
//  private val featureFieldNames = Seq("DP", "NALT", "NHET", "NMIN", "NVAR", "PASS", "PASS_S", "QUAL", "RATE", "SING", "TITV")
//  private val outputField = "GROUP"

  private val dataSetId = "ml.iris"
  private val featureFieldNames = Seq("petal-length", "petal-width", "sepal-length", "sepal-width")
  private val outputField = "class"

  override def run = {
    println("Logistic regression")

    val accuracy =
      machineLearningService.classify(dataSetId, featureFieldNames, outputField,
        LogisticRegression(
          maxIteration = Some(1000),
          regularization = Some(0.3),
          elasticMixingRatio = Some(0.8)
        )
      )
    println(accuracy)


    val rf = new RandomForestRegressor()
      .setLabelCol("label")
      .setFeaturesCol("indexedFeatures")

    val gbt = new GBTRegressor()
      .setLabelCol("label")
      .setFeaturesCol("indexedFeatures")
      .setMaxIter(10)

    val accuracy2 =
      machineLearningService.classify(dataSetId, featureFieldNames, outputField,
        LogisticRegression(
          family = Some(LogisticModelFamily.Multinomial),
          maxIteration = Some(1000),
          regularization = Some(0.3),
          elasticMixingRatio = Some(0.8)
        )
      )
    println(accuracy2)

    println("Decision tree")

    val accuracy3 =
      machineLearningService.classify(dataSetId, featureFieldNames, outputField,
        DecisionTree(
          maxDepth = Some(10)
        )
      )
    println(accuracy3)

    println("Random forest")

    val accuracy4 =
      machineLearningService.classify(dataSetId, featureFieldNames, outputField,
        DecisionTree(
          maxDepth = Some(10)
        )
      )
    println(accuracy4)

//    println("GBT Classifier")
//
//    val accuracy5 =
//      machineLearningService.classify(dataSetId, featureFieldNames, outputField,
//        GradientBoostTree(
//          maxIteration = Some(50)
//        )
//      )
//    println(accuracy5)

    println("Multilayer Perceptron Classifier")

    val accuracy6 =
      machineLearningService.classify(dataSetId, featureFieldNames, outputField,
        MultiLayerPerceptron(
          layers = Seq(featureFieldNames.size, 10, 10, 3),
          blockSize = Some(100),
          seed = Some(1234),
          maxIteration = Some(1000)
        )
      )
    println(accuracy6)

    println("Naive Bayes Classifier")

    val accuracy7 =
      machineLearningService.classify(dataSetId, featureFieldNames, outputField,
        NaiveBayes(
          modelType = Some(BayesModelType.multinomial)
        )
      )
    println(accuracy7)
  }
}

object MachineLearningTest extends GuiceBuilderRunnable[MachineLearningTest] with App { run }