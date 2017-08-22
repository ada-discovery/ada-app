package runnables

import javax.inject.Inject

import models.ml.TreeCore
import models.ml.regression._
import persistence.dataset.DataSetAccessorFactory
import services.DataSetService
import services.ml.MachineLearningService
import scala.concurrent.duration._

import scala.concurrent.Await.result

class MachineLearningRegressionTest @Inject()(
    machineLearningService: MachineLearningService,
    dsaf: DataSetAccessorFactory,
    dss: DataSetService
  ) extends Runnable {

  private val dataSetId = "ml.iris"
  private val featureFieldNames = Seq("petal-length", "petal-width", "sepal-length", "class")
  private val outputField = "sepal-width"

  override def run = {
    val dsa = dsaf(dataSetId).get
    val (jsons, fields) = result(dss.loadDataAndFields(dsa), 2 minutes)
    val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))

    // featureFieldNames,

    def regress(model: Regression) =
      machineLearningService.regress(jsons, fieldNameAndSpecs, outputField, model)

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

    val error3 = regress(RegressionTree(core = TreeCore(maxDepth = Some(10))))
    println(error3)

    println("Random regression forest")

    val error4 = regress(RandomRegressionForest(core = TreeCore(maxDepth = Some(10))))
    println(error4)
    println

    println("GBT regression")

    val error5 = regress(GradientBoostRegressionTree(maxIteration = Some(50)))
    println(error5)
  }
}

object MachineLearningRegressionTest extends GuiceBuilderRunnable[MachineLearningRegressionTest] with App { run }