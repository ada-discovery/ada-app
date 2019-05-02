package runnables.ml

import javax.inject.Inject

import org.incal.play.GuiceRunnableApp
import org.ada.server.services.SparkApp

import scala.math.random

class SparkPi @Inject() (sparkApp: SparkApp) extends Runnable {

  override def run = {
    val slices = 5
    val n = math.min(100000L * slices, Int.MaxValue).toInt // avoid overflow
    val count = sparkApp.sc.parallelize(1 until n, slices).map { i =>
      val x = random * 2 - 1
      val y = random * 2 - 1
      if (x*x + y*y < 1) 1 else 0
    }.reduce(_ + _)

    println("Pi is roughly " + 4.0 * count / (n - 1))
    sparkApp.session.stop()
  }
}

object SparkPi extends GuiceRunnableApp[SparkPi]