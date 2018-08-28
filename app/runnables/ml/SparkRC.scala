package runnables.ml

import javax.inject.Inject
import java.{lang => jl}

import com.banda.math.domain.rand.{RandomDistribution, RepeatedDistribution}
import com.banda.network.domain.{ActivationFunctionType, ReservoirSetting}
import org.apache.spark.ml.linalg.Vectors
import runnables.GuiceBuilderRunnable
import services.SparkApp
import services.ml.transformers.RCStatesWindowFactory

class SparkRC @Inject() (
  sparkApp: SparkApp,
  rcStatesWindowFactory: RCStatesWindowFactory
) extends Runnable {

  import sparkApp.sqlContext.implicits._

  private val reservoirSetting = ReservoirSetting(
    inputNodeNum = 2,
    reservoirNodeNum = 10,
    bias = 0,
    nonBiasInitial = 0,
    reservoirInDegree = Some(4),
    reservoirBias = true,
    inputReservoirConnectivity = 0.2,
    reservoirFunctionType = ActivationFunctionType.Linear,
    reservoirFunctionParams = Seq(1d),
    reservoirSpectralRadius = None,
//    weightDistribution = new RepeatedDistribution(Array(1d))
    weightDistribution = RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d)
  )

  private val df = Seq(
    (1, Vectors.dense(1, 2), -1),
    (2, Vectors.dense(4, 5), 2),
    (3, Vectors.dense(7, 8), -3),
    (5, Vectors.dense(10, 11), 8),
    (4, Vectors.dense(-2, 1), 4),
    (6, Vectors.dense(-9, 5), 10)
  ).toDF("time", "features", "label")

  private val df2 = Seq(
    (1, Vectors.dense(1, 2), -1),
    (2, Vectors.dense(4, 5), 2),
    (3, Vectors.dense(7, 8), -3),
    (5, Vectors.dense(10, 11), 8),
    (4, Vectors.dense(-2, 1), 4),
    (6, Vectors.dense(-9, 5), 10)
  ).toDF("time", "features", "label")

  private val windowSize = 3

  override def run = {
    df.show()

    val (rcTransform, _) = rcStatesWindowFactory(reservoirSetting, "features", "time", "rc_states")

    val rcDf1 = rcTransform.transform(df)
    rcDf1.show(truncate = false)

    val rcDf2 = rcTransform.transform(df2)
    rcDf2.show(truncate = false)

    sparkApp.session.stop()
  }
}

object SparkRC extends GuiceBuilderRunnable[SparkRC] with App { run }