package runnables.core

import javax.inject.Inject

import dataaccess.ClassificationResultRepoFactory
import play.api.Logger
import org.incal.core.InputFutureRunnable
import models._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class RemoveClassificationBinCurves @Inject()(repoFactory: ClassificationResultRepoFactory) extends InputFutureRunnable[RemoveClassificationBinCurvesSpec] {

  private val logger = Logger // (this.getClass())

  override def runAsFuture(input: RemoveClassificationBinCurvesSpec) = {
    val repo = repoFactory(input.dataSetId)

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