package runnables.dl4j

import com.banda.core.plotter.{Plotter, SeriesPlotSetting}
import org.incal.core.util.writeStringAsStream

import scala.io.{Source, StdIn}

object PlotMe extends App {
  private val plotter = Plotter("svg")

  println("Enter a file name you want to plot:")
  val fileName = StdIn.readLine()

  val lines = Source.fromFile(fileName).getLines()
  val captions = lines.next().split(",").map(_.trim).tail

  val data = lines.map {
    _.split(",").map(_.trim.toDouble)
  }.toSeq.transpose

  val xAxis = data.head
  val series = data.tail
  val settings = new SeriesPlotSetting().setTitle("Lala").setCaptions(captions).setXAxis(xAxis)
  val output = plotter.plotSeries(series, settings)
  writeStringAsStream(output, new java.io.File(fileName + ".svg"))
}
