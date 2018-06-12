package runnables.core

import javax.inject.Inject

import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import runnables.InputFutureRunnable

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class RemoveUnreferencedFilters @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnable[RemoveUnreferencedFiltersSpec] {

  private val logger = Logger

  override def runAsFuture(
    input: RemoveUnreferencedFiltersSpec
  ) = {
    val dsa = dsaf(input.dataSetId).get

    for {
      // get all the view for a given data set
      views <- dsa.dataViewRepo.find()

      // get all the filters for a given data set
      allFilters <- dsa.filterRepo.find()

      // remove unreferenced filters
      _ <- {
        val refFilterIds = views.flatMap(_.filterOrIds.collect{ case Right(filterId) => filterId }).toSet
        val allFilterIds = allFilters.flatMap(_._id).toSet

        val unreferencedFilterIds = allFilterIds.filterNot(refFilterIds.contains(_))

        logger.info(s"Removing ${unreferencedFilterIds.size} unreferenced filters for the data set ${input.dataSetId}.")

        dsa.filterRepo.delete(unreferencedFilterIds)
      }
    } yield
      ()
  }

  override def inputType = typeOf[RemoveUnreferencedFiltersSpec]
}

case class RemoveUnreferencedFiltersSpec(
  dataSetId: String
)
