package runnables.core

import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import runnables.InputFutureRunnable
import services.stats.StatsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CalcCorrelationsFromFile @Inject() (statsService: StatsService) extends InputFutureRunnable[CalcCorrelationsFromFileSpec] {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: CalcCorrelationsFromFileSpec) =
    for {
      // create a double-value file source and retrieve the field names
      (source, fieldNames) <- FeatureMatrixIO.load(input.inputFileName, input.skipFirstColumns)

      // calc correlations
      correlations <- statsService.pearsonCorrelationAllDefinedExec.execStreamed(input.streamParallelism, input.streamParallelism)(source)
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