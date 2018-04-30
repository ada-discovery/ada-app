package services.stats

import java.io.File
import com.jujutsu.tsne.barneshut.BHTSne
import com.jujutsu.tsne.barneshut.ParallelBHTsne
import com.jujutsu.utils.MatrixUtils
import com.jujutsu.utils.TSneUtils
import smile.plot.Palette
import smile.plot.plot

object LejonTSNEExample extends App {

  val folder = "/home/peter/Downloads/smile/shell/src/universal/data/mnist/"

  val initial_dims = 55
  val perplexity = 20.0
  val parallel = false

  val input: Array[Array[Double]] = MatrixUtils.simpleRead2DMatrix(new File(folder + "mnist2500_X.txt"), "   ")

  println("# rows   : " + input.length)
  println("# columns: " + input(0).length)

  var tsne = if (parallel) new ParallelBHTsne else new BHTSne
  val config = TSneUtils.buildConfig(input, 2, initial_dims, perplexity, 1000)

  val output = tsne.tsne(config)
  plot(output, 'o', Palette.RED)
}