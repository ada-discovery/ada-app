package runnables.ml

import javax.inject.Inject
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.incal.play.GuiceRunnableApp
import org.incal.spark_ml.models.TreeCore
import org.incal.spark_ml.models.regression._
import org.ada.server.services.DataSetService
import org.ada.server.services.ml.MachineLearningService
import org.incal.core.runnables.FutureRunnable

import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MachineLearningRegressionTest @Inject()(
    machineLearningService: MachineLearningService,
    dsaf: DataSetAccessorFactory,
    dss: DataSetService
  ) extends FutureRunnable {

  private val dataSetId = "ml.iris"
  private val featureFieldNames = Seq("petal-length", "petal-width", "sepal-length", "class")
  private val outputField = "sepal-width"

  override def runAsFuture =
    dsaf.getOrError(dataSetId).map(runAux)

  private def runAux(dsa: DataSetAccessor) {
    val (jsons, fields) = result(dss.loadDataAndFields(dsa), 2 minutes)
    val fieldNameAndSpecs = fields.map(field => (field.name, field.fieldTypeSpec))

    // featureFieldNames,

    def regress(model: Regressor) = {
      val resultFuture = machineLearningService.regressStatic(jsons, fieldNameAndSpecs, outputField, model)
      result(resultFuture, 1 hour)
    }

    println("Linear regression")

    val error = regress(
      LinearRegression(
        maxIteration = Left(Some(10)),
        regularization = Left(Some(0.3)),
        elasticMixingRatio = Left(Some(0.8))
      )
    )

    println(error)
    println

    println("Generalized linear regression")

    val error2 = regress(
      GeneralizedLinearRegression(
        family = Some(GeneralizedLinearRegressionFamily.Gaussian),
        link = Some(GeneralizedLinearRegressionLinkType.Identity),
        maxIteration = Left(Some(10)),
        regularization = Left(Some(0.3))
      )
    )
    println(error2)
    println

    println("Regression tree")

    val error3 = regress(RegressionTree(core = TreeCore(maxDepth = Left(Some(10)))))
    println(error3)

    println("Random regression forest")

    val error4 = regress(RandomRegressionForest(core = TreeCore(maxDepth = Left(Some(10)))))
    println(error4)
    println

    println("GBT regression")

    val error5 = regress(GradientBoostRegressionTree(maxIteration = Left(Some(50))))
    println(error5)
  }
}

object MachineLearningRegressionTest extends GuiceRunnableApp[MachineLearningRegressionTest]