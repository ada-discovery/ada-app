package runnables.core

import scala.util.Random
import org.incal.core.util.writeStringAsStream

object GenerateRandomCsv extends App {

  private val fileName = "randomset1.csv"
  private val size = 2000
  private val nullChance = 0.01

  private val header = Seq(
    randomString(2),
    randomString(2),
    randomString(2),
    randomString(2)
  ).mkString(",")

  private def withNull(value: Any) =
    if (Random.nextDouble < nullChance) "" else value.toString

  private def randomString(length: Int) =
    Random.alphanumeric.take(length).mkString

  val content = for (i <- 1 to size) yield {
    val col1 = withNull(Random.nextInt(10))
    val col2 = withNull(randomString(1))
    val col3 = withNull(randomString(5))
    val col4 = withNull(Random.nextInt(100))

    Seq(col1, col2, col3, col4).mkString(",")
  }

  writeStringAsStream((Seq(header) ++ content).mkString("\n"), new java.io.File(fileName))
}
