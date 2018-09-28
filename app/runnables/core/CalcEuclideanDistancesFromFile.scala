package runnables.core

import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.incal.core.InputFutureRunnable
import services.stats.StatsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CalcEuclideanDistancesFromFile @Inject() (statsService: StatsService) extends InputFutureRunnable[CalcEuclideanDistancesFromFileSpec] {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: CalcEuclideanDistancesFromFileSpec) =
    for {
      // create a double-value file source and retrieve the field names
      (source, fieldNames) <- FeatureMatrixIO.load(input.inputFileName, input.skipFirstColumns)

      // calc Euclidean distances
      distances <- statsService.euclideanDistanceAllDefinedExec.execStreamed(input.streamParallelism, input.streamParallelism)(source)
    } yield
      FeatureMatrixIO.saveSquare(
        distances,
        fieldNames,
        input.exportFileName,
        (value: Double) => value.toString
      )

  override def inputType = typeOf[CalcEuclideanDistancesFromFileSpec]
}

case class CalcEuclideanDistancesFromFileSpec(
  inputFileName: String,
  skipFirstColumns: Option[Int],
  streamParallelism: Option[Int],
  exportFileName: String
)