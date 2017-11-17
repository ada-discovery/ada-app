package runnables.core

import play.api.Logger
import runnables.DsaInputFutureRunnable
import models._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class RemoveClassificationBinCurves extends DsaInputFutureRunnable[RemoveClassificationBinCurvesSpec] {

  private val logger = Logger // (this.getClass())

  override def runAsFuture(input: RemoveClassificationBinCurvesSpec) = {
    val dsa_ = dsa(input.dataSetId)
    val repo = dsa_.classificationResultRepo

    for {
      // get all the results
      allResults <- repo.find()

      _ <- {
        val newResults = allResults.map { result =>
          result.copy(trainingBinCurves = Nil, testBinCurves = Nil, replicationBinCurves = Nil)
        }
        repo.update(newResults)
      }
    } yield
      ()
  }

  override def inputType = typeOf[RemoveClassificationBinCurvesSpec]
}

case class RemoveClassificationBinCurvesSpec(dataSetId: String)