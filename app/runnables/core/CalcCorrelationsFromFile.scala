package runnables.core

import javax.inject.Inject

import runnables.InputFutureRunnable
import services.stats.StatsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CalcCorrelationsFromFile @Inject() (statsService: StatsService) extends InputFutureRunnable[CalcCorrelationsFromFileSpec] {

  override def runAsFuture(input: CalcCorrelationsFromFileSpec) =
    for {
      // create a double-value file source and retrieve the field names
      (source, fieldNames) <- FeatureMatrixIO.load(input.inputFileName, input.skipFirstColumns)

      // calc correlations
      correlations <- statsService.calcPearsonCorrelationsAllDefinedStreamed(source, fieldNames.size, input.streamParallelism)
    } yield
      FeatureMatrixIO.saveSquare(
        correlations,
        fieldNames,
        input.exportFileName,
        (value: Option[Double]) => value.map(_.toString).getOrElse("")
      )

  override def inputType = typeOf[CalcCorrelationsFromFileSpec]
}

case class CalcCorrelationsFromFileSpec(
  inputFileName: String,
  skipFirstColumns: Option[Int],
  streamParallelism: Option[Int],
  exportFileName: String
)