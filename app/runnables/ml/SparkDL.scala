package runnables.ml

import javax.inject.Inject
import org.apache.spark.ml.{Pipeline, PipelineStage}
import org.apache.spark.ml.linalg.Vectors
import org.incal.play.GuiceRunnableApp
import org.ada.server.services.SparkApp
import org.incal.spark_ml.transformers.{SeqShift, SeqShiftWithConsecutiveOrder, SlidingWindow, SlidingWindowWithConsecutiveOrder}

class SparkDL @Inject() (sparkApp: SparkApp) extends Runnable {

  import sparkApp.sqlContext.implicits._

  private val df = Seq(
    (1, Vectors.dense(1, 2), -1),
    (2, Vectors.dense(4, 5), 2),
    (3, Vectors.dense(7, 8), -3),
    (5, Vectors.dense(10, 11), 8),
    (4, Vectors.dense(-2, 1), 4),
    (6, Vectors.dense(-9, 5), 10)
  ).toDF("time", "features", "label")

  private val df2 = Seq(
    (1, Array(1, 2), -1),
    (2, Array(4, 5), 2),
    (3, Array(7, 8), -3),
    (5, Array(10, 11), 8),
    (4, Array(-2, 1), 4),
    (6, Array(-9, 5), 10)
  ).toDF("time", "features", "label")

  private val windowSize = 3
  private val shift = 1

  private val (slidingWindow, _) = SlidingWindow.applyInPlace("features", "time")(Left(Some(windowSize)))
  private val (slidingWindowWithConsecutiveOrder, _) = SlidingWindowWithConsecutiveOrder.applyInPlace("features", "time")(Left(Some(windowSize)))
  private val seqShift = SeqShift("label", "time", "shiftLabel")(shift)
  private val seqShiftWithConsecutiveOrder = SeqShiftWithConsecutiveOrder("label", "time", "shiftLabel")(shift)

  override def run = {
    df.show()

    val windowDf = asPipeline(slidingWindow).fit(df).transform(df)
    windowDf.show(20, false)

    val windowDf2 = asPipeline(slidingWindowWithConsecutiveOrder).fit(df).transform(df)
    windowDf2.show(20, false)

    val dlDf = seqShift.transform(windowDf)
    dlDf.show(20, false)

    val dlDf2 = seqShiftWithConsecutiveOrder.transform(windowDf)
    dlDf2.show(20, false)

    sparkApp.session.stop()
  }

  private def asPipeline(stage: PipelineStage) =
    new Pipeline().setStages(Array(stage))
}

object SparkDL extends GuiceRunnableApp[SparkDL]