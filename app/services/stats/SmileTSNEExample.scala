package services.stats

import scala.language.dynamics
import scala.language.implicitConversions
import scala.collection.JavaConverters._
import smile._
import smile.data._
import smile.plot.plot
import smile.plot.Palette
import smile.manifold.{Operators => ManifoldOperators}
import smile.projection.{Operators => ProjectionOperators}

object SmileTSNEExample extends App with ManifoldOperators with ProjectionOperators {

  private val folder = "/home/peter/Downloads/smile/shell/src/universal/data/mnist/"

  println("Reading the data")
  val mnist = read.table(folder + "mnist2500_X.txt")
  val label = read.table(folder + "mnist2500_labels.txt")
  val x = mnist.unzip
  val y = label.unzip.map(_(0).toInt)
  val legend = label.unzip.map(_(0).toString)

  println("Calculating PCA")
  println("# Items                  :" + x.length)
  println("Length of row            :" + x(0).length)
  println

  val pc = pca(x)
  pc.setProjection(50)
  val x50 = pc.project(x)

  println("After PCA - # Items      : " + x50.length)
  println("After PCA - Length of row: " + x50(0).length)
  println
  println("Performing T-SNE")
  val sne = tsne(x50, 3, 20, 200, 1000)

  println("Plotting")
  plot(sne.getCoordinates, y, 'o', Palette.COLORS)
}
