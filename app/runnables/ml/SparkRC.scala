package runnables.ml

import javax.inject.Inject
import java.{lang => jl}

import com.banda.math.domain.rand.{RandomDistribution, RepeatedDistribution}
import com.banda.network.domain.{ActivationFunctionType, ReservoirSetting}
import models.ml.timeseries.ReservoirSpec
import org.apache.spark.ml.linalg.Vectors
import org.incal.play.GuiceRunnableApp
import services.SparkApp
import services.ml.transformers.RCStatesWindowFactory

class SparkRC @Inject() (
  sparkApp: SparkApp,
  rcStatesWindowFactory: RCStatesWindowFactory
) extends Runnable {

  import sparkApp.sqlContext.implicits._

  private val reservoirSpec = ReservoirSpec(
    inputNodeNum = 2,
    reservoirNodeNum = Left(Some(10)),
    bias = 0,
    nonBiasInitial = 0,
    reservoirInDegree = Left(Some(4)),
    reservoirBias = true,
    inputReservoirConnectivity = Left(Some(0.2)),
    reservoirFunctionType = ActivationFunctionType.Linear,
    reservoirFunctionParams = Seq(1d),
    reservoirSpectralRadius = Left(None),
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

    val (rcTransform, _) = rcStatesWindowFactory.applyWoWashout("features", "time", "rc_states")(reservoirSpec)

    val rcDf1 = rcTransform.transform(df)
    rcDf1.show(truncate = false)

    val rcDf2 = rcTransform.transform(df2)
    rcDf2.show(truncate = false)

    sparkApp.session.stop()
  }
}

object SparkRC extends GuiceRunnableApp[SparkRC]