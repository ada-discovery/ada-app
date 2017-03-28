package runnables

import javax.inject.Inject

import models.ml.classification._
import services.ml.MachineLearningService

class MachineLearningClassificationTest @Inject()(machineLearningService: MachineLearningService) extends Runnable {

//  private val dataSetId = "ppmi.ppmi_si"
//  private val featureFieldNames = Seq("DP", "NALT", "NHET", "NMIN", "NVAR", "PASS", "PASS_S", "QUAL", "RATE", "SING", "TITV")
//  private val outputField = "GROUP"

  private val dataSetId = "ml.iris"
  private val featureFieldNames = Seq("petal-length", "petal-width", "sepal-length", "sepal-width")
  private val outputField = "class"

  override def run = {
    def classify(model: Classification) =
      machineLearningService.classify(dataSetId, featureFieldNames, outputField, model)

    println("Logistic regression")

    val accuracy = classify(
        LogisticRegression(
          maxIteration = Some(1000),
          regularization = Some(0.3),
          elasticMixingRatio = Some(0.8)
        )
      )

    println(accuracy)
    println

    println("Multinomial Logistic regression")

    val accuracy2 = classify(
        LogisticRegression(
          family = Some(LogisticModelFamily.Multinomial),
          maxIteration = Some(1000),
          regularization = Some(0.3),
          elasticMixingRatio = Some(0.8)
        )
      )
    println(accuracy2)
    println

    println("Decision tree")

    val accuracy3 = classify(DecisionTree(maxDepth = Some(10)))
    println(accuracy3)

    println("Random forest")

    val accuracy4 = classify(RandomForest(maxDepth = Some(10)))
    println(accuracy4)
    println

//    println("GBT Classifier")
//
//    val accuracy5 = classify(GradientBoostTree(maxIteration = Some(50)))
//    println(accuracy5)
//    println

    println("Multilayer Perceptron Classifier")

    val accuracy6 = classify(
        MultiLayerPerceptron(
          layers = Seq(featureFieldNames.size, 10, 10, 3),
          blockSize = Some(100),
          seed = Some(1234),
          maxIteration = Some(1000)
        )
      )
    println(accuracy6)
    println

    println("Naive Bayes Classifier")

    val accuracy7 = classify(
        NaiveBayes(
          modelType = Some(BayesModelType.multinomial)
        )
      )
    println(accuracy7)
  }
}

object MachineLearningClassificationTest extends GuiceBuilderRunnable[MachineLearningClassificationTest] with App { run }