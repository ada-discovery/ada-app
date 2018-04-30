package services.stats

import java.io.File
import com.jujutsu.tsne.barneshut.BHTSne
import com.jujutsu.tsne.barneshut.BarnesHutTSne
import com.jujutsu.tsne.barneshut.ParallelBHTsne
import com.jujutsu.utils.MatrixOps
import com.jujutsu.utils.MatrixUtils
import com.jujutsu.utils.TSneUtils
import com.jujutsu.tsne.TSneConfiguration
import smile.plot.Palette
import smile.plot.plot

object TSneTest2 extends App {
    val folder: String = "/home/peter/Downloads/smile/shell/src/universal/data/mnist/"

    val initial_dims: Int = 55
    val perplexity: Double = 20.0

    val X: Array[Array[Double]] = MatrixUtils.simpleRead2DMatrix(new File(folder + "mnist2500_X.txt"), "   ")
    System.out.println(MatrixOps.doubleArrayToPrintString(X, ", ", 50, 10))
    var tsne: BarnesHutTSne = null
    val parallel: Boolean = false
    if (parallel) {
      tsne = new ParallelBHTsne
    }
    else {
      tsne = new BHTSne
    }
    val config: TSneConfiguration = TSneUtils.buildConfig(X, 2, initial_dims, perplexity, 1000)
    val Y: Array[Array[Double]] = tsne.tsne(config)
    // Plot Y or save Y to file and plot with some other tool such as for instance R
    plot(Y, 'o', Palette.RED)
}