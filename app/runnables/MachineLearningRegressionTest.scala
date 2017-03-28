package runnables

import javax.inject.Inject

import models.ml.regression._
import services.ml.MachineLearningService

class MachineLearningRegressionTest @Inject()(machineLearningService: MachineLearningService) extends Runnable {

  private val dataSetId = "ml.iris"
  private val featureFieldNames = Seq("petal-length", "petal-width", "sepal-length", "class")
  private val outputField = "sepal-width"

  override def run = {
    def regress(model: Regression) =
      machineLearningService.regress(dataSetId, featureFieldNames, outputField, model)

    println("Linear regression")

    val error = regress(
      LinearRegression(
        maxIteration = Some(10),
        regularization = Some(0.3),
        elasticMixingRatio = Some(0.8)
      )
    )

    println(error)
    println

    println("Generalized linear regression")

    val error2 = regress(
      GeneralizedLinearRegression(
        family = Some(GeneralizedLinearRegressionFamily.Gaussian),
        link = Some(GeneralizedLinearRegressionLinkType.Identity),
        maxIteration = Some(10),
        regularization = Some(0.3)
      )
    )
    println(error2)
    println

    println("Regression tree")

    val error3 = regress(RegressionTree(maxDepth = Some(10)))
    println(error3)

    println("Random regression forest")

    val error4 = regress(RandomRegressionForest(maxDepth = Some(10)))
    println(error4)
    println

    println("GBT regression")

    val error5 = regress(GradientBoostRegressionTree(maxIteration = Some(50)))
    println(error5)
  }
}

object MachineLearningRegressionTest extends GuiceBuilderRunnable[MachineLearningRegressionTest] with App { run }