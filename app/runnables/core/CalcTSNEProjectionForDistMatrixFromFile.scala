package runnables.core

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.google.inject.Inject
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import runnables.InputFutureRunnable
import runnables.core.CalcUtil._
import services.stats.{StatsService, TSNESetting}
import smile.plot._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CalcTSNEProjectionForDistMatrixFromFile @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends InputFutureRunnable[CalcTSNEProjectionForDistMatrixFromFileSpec] {

  import statsService._

  private val logger = Logger

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  def runAsFuture(input: CalcTSNEProjectionForDistMatrixFromFileSpec) = {
    for {
    // create a double-value file source and retrieve the field names
      (source, fieldNames) <- FeatureMatrixIO.loadArray(input.inputFileName, Some(1))

      // fully load everything from the source
      inputs <- source.runWith(Sink.seq)
    } yield {

      // aux function
      def withDefault[T](default: T)(values: Seq[T]) =
        values match {
          case Nil => Seq(default)
          case _ => values
        }

      for {
        perplexity <- withDefault(20d)(input.perplexities)
        eta <- withDefault(100d)(input.etas)
        iterations <- withDefault(1000)(input.iterations)
      } yield {
        val arrayInputs = inputs.toArray

        // prepare the setting
        val setting = TSNESetting(
          dim = input.dims,
          iterations = iterations,
          perplexity = perplexity,
          eta = eta
        )

        val expotFileName = s"${input.exportFileName}-${input.dims}d_iter-${iterations}_per-${perplexity}_eta-${eta}"

        val plotExportFileName = if (input.exportPlot) Some(expotFileName + ".png") else None

        runAndExportAux(
          input.inputFileName,
          arrayInputs,
          fieldNames)(
          setting,
          expotFileName + ".csv",
          plotExportFileName
        )
      }
    }
  }

  private def runAndExportAux(
    inputFileName: String,
    inputs: Array[Array[Double]],
    fieldNames: Seq[String])(
    setting: TSNESetting,
    exportFileName: String,
    plotExportFileName: Option[String]
  ) = {
    // run t-SNE
    val tsneProjections = performTSNE(inputs, setting)
    logger.info(s"Distance-matrix-based t-SNE for a file ${inputFileName} finished.")

    // image export
    if (plotExportFileName.isDefined) {
      val tsneFailed = tsneProjections.exists(_.exists(_.isNaN))
      if (tsneFailed)
        logger.error(s"Distance-matrix-based t-SNE for a file ${inputFileName} returned NaN values. Image export not possible.")
      else {
        val labels = tsneProjections.map(_ => 0)
        val canvas = ScatterPlot.plot(tsneProjections, labels, 'o', Palette.COLORS)
        saveCanvasAsImage(plotExportFileName.get, 1000, 800)(canvas)
      }
    }

    // save the results
    FeatureMatrixIO.save(
      tsneProjections.map(_.toSeq),
      fieldNames,
      for (i <- 1 to setting.dim) yield "x" + i,
      "featureName",
      exportFileName,
      (value: Double) => value.toString
    )
  }

  override def inputType = typeOf[CalcTSNEProjectionForDistMatrixFromFileSpec]
}

case class CalcTSNEProjectionForDistMatrixFromFileSpec(
  inputFileName: String,
  dims: Int,
  iterations: Seq[Int],
  perplexities: Seq[Double],
  etas: Seq[Double],
  exportFileName: String,
  exportPlot: Boolean
)