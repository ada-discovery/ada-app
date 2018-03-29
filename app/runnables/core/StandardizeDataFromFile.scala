package runnables.core

import javax.inject.Inject

import play.api.Logger
import runnables.InputFutureRunnable
import services.stats.StatsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class StandardizeDataFromFile @Inject() (statsService: StatsService) extends InputFutureRunnable[StandardizeDataFromFileSpec] {

  private val logger = Logger

  override def runAsFuture(input: StandardizeDataFromFileSpec) =
    for {
      // create a double-value file source and retrieve the field names
      (source, fieldNames) <- FeatureMatrixIO.load(input.inputFileName, None)

      // perform metric MDS
      standardizedValues <- {
        val sourceWithOptional = source.map(_.map(Some(_)))
        statsService.standardize(sourceWithOptional, input.useSampleStd)
      }
    } yield {
      logger.info(s"Exporting the standardized data to ${input.exportFileName}.")

      FeatureMatrixIO.savePlain(
        standardizedValues,
        fieldNames,
        input.exportFileName,
        (value: Option[Double]) => value.map(_.toString).getOrElse("")
      )
    }

  override def inputType = typeOf[StandardizeDataFromFileSpec]
}

case class StandardizeDataFromFileSpec(
  inputFileName: String,
  useSampleStd: Boolean,
  exportFileName: String
)