package runnables.ml

import javax.inject.Inject
import org.incal.play.GuiceRunnableApp
import org.ada.server.services.SparkApp
import org.incal.core.runnables.RunnableHtmlOutput

import scala.math.random

class SparkPi @Inject() (sparkApp: SparkApp) extends Runnable with RunnableHtmlOutput {

  override def run = {
    val slices = 5
    val n = math.min(100000L * slices, Int.MaxValue).toInt // avoid overflow
    val count = sparkApp.sc.parallelize(1 until n, slices).map { i =>
      val x = random * 2 - 1
      val y = random * 2 - 1
      if (x*x + y*y < 1) 1 else 0
    }.reduce(_ + _)

    addParagraph(s"Pi is roughly ${bold((4.0 * count / (n - 1)).toString)}")
  }
}

object SparkPi extends GuiceRunnableApp[SparkPi]