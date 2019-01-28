package runnables.ml

import javax.inject.Inject

import persistence.dataset.DataSetAccessorFactory
import org.incal.play.GuiceRunnableApp
import org.incal.spark_ml.models.TreeCore
import org.incal.spark_ml.models.classification._
import services.DataSetService
import services.ml.MachineLearningService
import services.stats.StatsService

import scala.concurrent.Await.result
import scala.concurrent.duration._

class MachineLearningClassificationTest @Inject()(
    machineLearningService: MachineLearningService,
    statsService: StatsService,
    dsaf: DataSetAccessorFactory,
    dss: DataSetService
  ) extends Runnable {

//  private val dataSetId = "ppmi.ppmi_si"
//  private val featureFieldNames = Seq("DP", "NALT", "NHET", "NMIN", "NVAR", "PASS", "PASS_S", "QUAL", "RATE", "SING", "TITV")
//  private val outputField = "GROUP"

  private val dataSetId = "ml.iris"
  private val featureFieldNames = Seq("petal-length", "petal-width", "sepal-length", "sepal-width")
  private val outputFieldName = "class"

  override def run = {
    val dsa = dsaf(dataSetId).get
    val (jsons, fields) = result(dss.loadDataAndFields(dsa), 2 minutes)
    val fieldNameSpecs = fields.map(field => (field.name, field.fieldTypeSpec))

    // featureFieldNames,


    def classify(model: Classifier) = {
      val resultFuture = machineLearningService.classifyStatic(jsons, fieldNameSpecs, outputFieldName, model)
      result(resultFuture, 1 hour)
    }

    println("Logistic regression")

    val accuracy = classify(
        LogisticRegression(
          maxIteration = Left(Some(1000)),
          regularization = Left(Some(0.3)),
          elasticMixingRatio = Left(Some(0.8))
        )
      )

    println(accuracy)
    println

    println("Multinomial Logistic regression")

    val accuracy2 = classify(
        LogisticRegression(
          family = Some(LogisticModelFamily.Multinomial),
          maxIteration = Left(Some(1000)),
          regularization = Left(Some(0.3)),
          elasticMixingRatio = Left(Some(0.8))
        )
      )
    println(accuracy2)
    println

    println("Decision tree")

    val accuracy3 = classify(DecisionTree(core = TreeCore(maxDepth = Left(Some(10)))))
    println(accuracy3)

    println("Random forest")

    val accuracy4 = classify(RandomForest(core = TreeCore(maxDepth = Left(Some(10)))))
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
          hiddenLayers = Seq(featureFieldNames.size, 10, 10, 3),
          blockSize = Left(Some(100)),
          seed = Some(1234),
          maxIteration = Left(Some(1000))
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

object MachineLearningClassificationTest extends GuiceRunnableApp[MachineLearningClassificationTest]